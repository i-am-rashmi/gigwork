package com.gigwork.core.hiring.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.gigs.GigLookup;
import com.gigwork.core.gigs.GigState;
import com.gigwork.core.hiring.ApplicantShortlisted;
import com.gigwork.core.hiring.config.HiringProperties;
import com.gigwork.core.hiring.domain.AlreadyShortlistedException;
import com.gigwork.core.hiring.domain.ApplicationNotActiveException;
import com.gigwork.core.hiring.domain.ApplicationNotFoundException;
import com.gigwork.core.hiring.domain.ContactMethodRequiredException;
import com.gigwork.core.hiring.domain.GigNotOpenException;
import com.gigwork.core.hiring.domain.NotShortlistedException;
import com.gigwork.core.hiring.domain.ShortlistActionLimitException;
import com.gigwork.core.hiring.domain.ShortlistFullException;
import com.gigwork.core.hiring.persistence.ApplicationStore;
import com.gigwork.core.hiring.persistence.ShortlistStore;
import com.gigwork.core.hiring.persistence.StoredApplication;
import com.gigwork.core.identity.ContactMethods;

/**
 * Shortlisting applicants and the consent to share contact details (FR-25, FR-25a, FR-26, NFR-38).
 *
 * <p>What can be read is checked first, with no lock and no write. Each write is then one transaction under the
 * Poster's lock (a gig has one Poster, so it also serialises the per-gig count): the shortlist is one INSERT ... ON CONFLICT, then the
 * limits are counted and the transaction is rolled back by an exception if one is exceeded, then the action is
 * logged. Removing never waits for or fails on the daily limit: it ends contact access, so it must always work; it
 * is logged and counts towards the limit of the next additions.
 */
@Service
public class ShortlistService {

	private static final Duration WINDOW = Duration.ofHours(24);

	private final ApplicationStore applications;
	private final ShortlistStore store;
	private final GigLookup gigs;
	private final ContactMethods contactMethods;
	private final ShortlistLocks locks;
	private final HiringProperties properties;
	private final TransactionTemplate transaction;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	ShortlistService(ApplicationStore applications, ShortlistStore store, GigLookup gigs, ContactMethods contactMethods,
			ShortlistLocks locks, HiringProperties properties, TransactionTemplate transaction,
			ApplicationEventPublisher events, Clock clock) {
		this.applications = applications;
		this.store = store;
		this.gigs = gigs;
		this.contactMethods = contactMethods;
		this.locks = locks;
		this.properties = properties;
		this.transaction = transaction;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * Shortlists an applicant, or adds a removed one again (their consent is then asked again).
	 *
	 * @throws ApplicationNotFoundException not the caller's gig's application (never says which)
	 * @throws ApplicationNotActiveException the application was withdrawn
	 * @throws com.gigwork.core.hiring.domain.GigNotOpenException the gig is not open
	 * @throws ContactMethodRequiredException {@code shareMyContact} but the Poster has no contact method
	 * @throws AlreadyShortlistedException the Talent is shortlisted already
	 * @throws ShortlistFullException the gig has the most Talent shortlisted at once
	 * @throws ShortlistActionLimitException the Poster used all their shortlist actions of the last 24 hours
	 */
	public ShortlistState shortlist(UUID posterId, UUID applicationId, boolean shareMyContact) {
		StoredApplication application = ownApplication(posterId, applicationId);
		if (!application.isActive()) {
			throw new ApplicationNotActiveException();
		}
		requireOpen(application.gigId());
		if (shareMyContact && !contactMethods.hasContactMethod(posterId)) {
			throw new ContactMethodRequiredException();
		}
		Instant now = clock.instant();
		transaction.executeWithoutResult(status -> {
			locks.lock(posterId);
			if (!store.shortlist(applicationId, posterId, shareMyContact, now)) {
				throw store.find(applicationId).filter(ShortlistStore.Entry::isCurrent).isPresent()
						? new AlreadyShortlistedException() : new ApplicationNotActiveException();
			}
			if (store.countCurrent(application.gigId()) > properties.shortlistPerGig()) {
				throw new ShortlistFullException(); // rolls the shortlist back
			}
			if (store.countActionsSince(posterId, now.minus(WINDOW)) >= properties.shortlistActionsPerDay()) {
				throw new ShortlistActionLimitException(properties.shortlistActionsPerDay());
			}
			store.logAction(posterId, applicationId, "SHORTLIST", now);
			events.publishEvent(new ApplicantShortlisted(applicationId, application.gigId(), posterId,
					application.talentId(), shareMyContact, now));
		});
		return new ShortlistState(applicationId, true, shareMyContact, false);
	}

	/**
	 * Removes a Talent from the shortlist. No notification. Always allowed, whatever the daily limit.
	 *
	 * @throws NotShortlistedException the Talent is not currently shortlisted
	 */
	public void remove(UUID posterId, UUID applicationId) {
		StoredApplication application = ownApplication(posterId, applicationId);
		Instant now = clock.instant();
		transaction.executeWithoutResult(status -> {
			locks.lock(posterId);
			if (!store.remove(applicationId, posterId, now)) {
				throw new NotShortlistedException();
			}
			store.logAction(posterId, applicationId, "REMOVE", now);
		});
	}

	/**
	 * The Poster's choice to share their own contact with the shortlisted Talent; can change at any time.
	 *
	 * @throws ContactMethodRequiredException {@code share} but the Poster has no contact method
	 * @throws NotShortlistedException the Talent is not currently shortlisted
	 */
	public ShortlistState setPosterShares(UUID posterId, UUID applicationId, boolean share) {
		StoredApplication application = ownApplication(posterId, applicationId);
		if (share && !contactMethods.hasContactMethod(posterId)) {
			throw new ContactMethodRequiredException();
		}
		transaction.executeWithoutResult(status -> {
			if (!store.setPosterShares(applicationId, posterId, share)) {
				throw new NotShortlistedException();
			}
		});
		var entry = store.find(application.id()).orElseThrow();
		return new ShortlistState(applicationId, entry.isCurrent(), entry.posterShares(), entry.talentConsents());
	}

	/**
	 * The applicant's consent to reveal their own contact to the Poster; can be withdrawn at any time, with no
	 * notification.
	 *
	 * @throws ApplicationNotFoundException not the caller's application
	 * @throws ContactMethodRequiredException {@code consent} but the Talent has no contact method
	 * @throws NotShortlistedException the Talent is not currently shortlisted
	 */
	public boolean setConsent(UUID talentId, UUID applicationId, boolean consent) {
		applications.find(applicationId).filter(a -> a.talentId().equals(talentId))
				.orElseThrow(ApplicationNotFoundException::new);
		if (consent && !contactMethods.hasContactMethod(talentId)) {
			throw new ContactMethodRequiredException();
		}
		Instant now = clock.instant();
		transaction.executeWithoutResult(status -> {
			if (!store.setConsent(applicationId, talentId, consent, now)) {
				throw new NotShortlistedException();
			}
		});
		return store.find(applicationId).orElseThrow().talentConsents();
	}

	private StoredApplication ownApplication(UUID posterId, UUID applicationId) {
		return applications.find(applicationId).filter(a -> a.posterId().equals(posterId))
				.orElseThrow(ApplicationNotFoundException::new);
	}

	private void requireOpen(UUID gigId) {
		gigs.find(gigId).filter(g -> g.status() == GigState.OPEN).orElseThrow(GigNotOpenException::new);
	}

}
