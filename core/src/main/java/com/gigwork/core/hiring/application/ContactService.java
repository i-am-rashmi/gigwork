package com.gigwork.core.hiring.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.gigs.GigLookup;
import com.gigwork.core.gigs.GigState;
import com.gigwork.core.gigs.GigSummary;
import com.gigwork.core.hiring.domain.ApplicationNotFoundException;
import com.gigwork.core.hiring.domain.ContactNotAvailableException;
import com.gigwork.core.hiring.config.HiringProperties;
import com.gigwork.core.hiring.persistence.ApplicationStore;
import com.gigwork.core.hiring.persistence.HireStore;
import com.gigwork.core.hiring.persistence.ShortlistStore;
import com.gigwork.core.hiring.persistence.ShortlistStore.Entry;
import com.gigwork.core.hiring.persistence.StoredApplication;
import com.gigwork.core.identity.ContactCard;
import com.gigwork.core.identity.ContactMethods;

/**
 * Viewing the other side's contact details (FR-26).
 *
 * <p>One transaction does it all: it re-reads every condition (the shortlist entry is locked FOR SHARE, so a removal
 * or a consent withdrawal at the same moment waits until the view is logged), fetches the details fresh from
 * Identity, writes the access log row and only then returns them. If any step fails nothing is returned and no log
 * row remains. Contact details are never copied: not into this module's tables, not into any list.
 *
 * <p>Accepted race: the other side withdraws consent (or the Poster stops sharing) at the very same instant as a
 * view. Either the view took the lock first and saw consent, and is logged, or it came second and was refused.
 * Either way, consent existed at the moment of every logged view.
 *
 * <p>Every refusal is the same {@link ContactNotAvailableException}, whatever the reason, so the answer never
 * tells the caller anything about the other person's choices. Only a stranger to the application gets a 404.
 */
@Service
public class ContactService {

	private final ApplicationStore applications;
	private final ShortlistStore store;
	private final HireStore hires;
	private final HiringProperties properties;
	private final GigLookup gigs;
	private final ContactMethods contactMethods;
	private final TransactionTemplate transaction;
	private final Clock clock;

	ContactService(ApplicationStore applications, ShortlistStore store, HireStore hires, HiringProperties properties,
			GigLookup gigs, ContactMethods contactMethods, TransactionTemplate transaction, Clock clock) {
		this.applications = applications;
		this.store = store;
		this.hires = hires;
		this.properties = properties;
		this.gigs = gigs;
		this.contactMethods = contactMethods;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** The Poster views the shortlisted Talent's contact; needs the Talent's consent. */
	public ContactView talentContact(UUID posterId, UUID applicationId) {
		StoredApplication application = applications.find(applicationId).filter(a -> a.posterId().equals(posterId))
				.orElseThrow(ApplicationNotFoundException::new);
		return view(posterId, application.talentId(), applicationId, Entry::talentConsents);
	}

	/** The Talent views the Poster's contact; needs the Poster to be sharing. */
	public ContactView posterContact(UUID talentId, UUID applicationId) {
		StoredApplication application = applications.find(applicationId).filter(a -> a.talentId().equals(talentId))
				.orElseThrow(ApplicationNotFoundException::new);
		return view(talentId, application.posterId(), applicationId, Entry::posterShares);
	}

	private ContactView view(UUID viewerId, UUID viewedId, UUID applicationId, java.util.function.Predicate<Entry> otherSideAgrees) {
		Instant now = clock.instant();
		return transaction.execute(status -> {
			Entry entry = store.findForView(applicationId).filter(Entry::isCurrent).filter(otherSideAgrees)
					.orElseThrow(ContactNotAvailableException::new);
			StoredApplication application = applications.find(applicationId).filter(StoredApplication::isActive)
					.orElseThrow(ContactNotAvailableException::new);
			GigSummary gig = gigs.find(application.gigId()).orElseThrow(ContactNotAvailableException::new);
			if (!gigAllowsViews(gig, applicationId, now)) {
				throw new ContactNotAvailableException();
			}
			ContactCard card = contactMethods.contactCard(viewedId).orElseThrow(ContactNotAvailableException::new);
			store.logView(viewerId, viewedId, entry.gigId(), applicationId, now); // if this fails, nothing is returned
			return reveal(card);
		});
	}

	/**
	 * Contacts can be viewed while the gig is open. Once it is filled, only the hired pair can, while their hire is
	 * confirmed and until {@code hiredContactDaysAfterGig} days after the gig ends; the other shortlisted Talent lose
	 * access when the gig is filled (FR-26). A gig that is cancelled or expired never allows a view.
	 */
	private boolean gigAllowsViews(GigSummary gig, UUID applicationId, Instant now) {
		if (gig.status() == GigState.OPEN) {
			return true;
		}
		return gig.status() == GigState.FILLED && hires.lockConfirmedOfApplication(applicationId).isPresent()
				&& now.isBefore(gig.endAt().plus(Duration.ofDays(properties.hiredContactDaysAfterGig())));
	}

	private static ContactView reveal(ContactCard card) {
		List<String> methods = new ArrayList<>();
		if (card.whatsapp()) {
			methods.add("WHATSAPP");
		}
		if (card.call()) {
			methods.add("CALL");
		}
		if (card.email()) {
			methods.add("EMAIL");
		}
		return new ContactView(List.copyOf(methods), card.whatsapp() || card.call() ? card.mobile() : null,
				card.email() ? card.emailAddress() : null);
	}

}
