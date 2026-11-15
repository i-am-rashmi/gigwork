package com.gigwork.core.hiring.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.availability.Availability;
import com.gigwork.core.availability.Conflicts;
import com.gigwork.core.gigs.GigLookup;
import com.gigwork.core.gigs.GigState;
import com.gigwork.core.gigs.GigSummary;
import com.gigwork.core.hiring.ApplicationSubmitted;
import com.gigwork.core.hiring.config.HiringProperties;
import com.gigwork.core.hiring.domain.AlreadyAppliedException;
import com.gigwork.core.hiring.domain.ApplicationNotFoundException;
import com.gigwork.core.hiring.domain.ApplyLimitReachedException;
import com.gigwork.core.hiring.domain.BusyPeriodsException;
import com.gigwork.core.hiring.domain.CategoryNotOnProfileException;
import com.gigwork.core.hiring.domain.GigNotOpenForApplicationsException;
import com.gigwork.core.hiring.domain.InvalidApplicationException;
import com.gigwork.core.hiring.domain.OwnGigException;
import com.gigwork.core.hiring.domain.ProfileNotActiveException;
import com.gigwork.core.hiring.persistence.ApplicationStore;
import com.gigwork.core.hiring.persistence.ApplicationStore.Submitted;
import com.gigwork.core.platform.ContactDetails;
import com.gigwork.core.profile.TalentEligibility;
import com.gigwork.core.profile.TalentProfiles;

/**
 * Applying to a gig (FR-23, NFR-39).
 *
 * <p>Every rule that can be answered by reading is checked first, with no lock and no write. The write is then one
 * transaction under the Talent's lock: count the submissions of the last 24 hours, then one INSERT ... ON CONFLICT
 * that either creates the application or makes a withdrawn one active again, then the submission row and the event.
 * The unique (gig, Talent) key decides two simultaneous applies to one gig; the lock makes the count and the
 * insert one step, so simultaneous applies to different gigs cannot pass the limit.
 *
 * <p>Accepted race: the gig may be cancelled, filled or expire between the check that it is open and the insert.
 * That is harmless: the application is just recorded, it shows the gig as closed on the Talent's dashboard, and
 * hiring checks the gig's status again before anything is decided.
 */
@Service
public class ApplyService {

	private static final Duration WINDOW = Duration.ofHours(24);

	private final ApplicationStore store;
	private final GigLookup gigs;
	private final TalentProfiles profiles;
	private final Availability availability;
	private final ApplicationLimits limits;
	private final TalentLock lock;
	private final HiringProperties properties;
	private final TransactionTemplate transaction;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	ApplyService(ApplicationStore store, GigLookup gigs, TalentProfiles profiles, Availability availability,
			ApplicationLimits limits, TalentLock lock, HiringProperties properties, TransactionTemplate transaction,
			ApplicationEventPublisher events, Clock clock) {
		this.store = store;
		this.gigs = gigs;
		this.profiles = profiles;
		this.availability = availability;
		this.limits = limits;
		this.lock = lock;
		this.properties = properties;
		this.transaction = transaction;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * @param acknowledgeBusy true when the Talent has seen the busy warning and applies anyway
	 * @throws InvalidApplicationException the note is too long or carries contact details
	 * @throws ProfileNotActiveException the Talent has no active profile
	 * @throws ApplicationNotFoundException no such gig
	 * @throws GigNotOpenForApplicationsException the gig is not open, or has started
	 * @throws OwnGigException the gig is the Talent's own
	 * @throws CategoryNotOnProfileException the gig's category is not on the profile
	 * @throws BusyPeriodsException the gig overlaps the Talent's calendar and this was not acknowledged
	 * @throws ApplyLimitReachedException the 24-hour limit is reached
	 * @throws AlreadyAppliedException the Talent already has an active application for the gig
	 */
	public Applied apply(UUID talentId, UUID gigId, String rawNote, boolean acknowledgeBusy) {
		String note = note(rawNote);
		Instant now = clock.instant();

		TalentEligibility talent = profiles.eligibility(talentId).filter(TalentEligibility::isActive)
				.orElseThrow(ProfileNotActiveException::new);
		GigSummary gig = gigs.find(gigId).orElseThrow(ApplicationNotFoundException::new);
		if (gig.status() != GigState.OPEN || !gig.startAt().isAfter(now)) {
			throw new GigNotOpenForApplicationsException();
		}
		if (gig.posterId().equals(talentId)) {
			throw new OwnGigException();
		}
		if (!talent.categoryIds().contains(gig.categoryId())) {
			throw new CategoryNotOnProfileException();
		}
		Conflicts conflicts = availability.conflicts(talentId, gig.startAt(), gig.endAt());
		if (conflicts.hasConflict() && !acknowledgeBusy) {
			throw new BusyPeriodsException(conflicts.periods());
		}
		boolean busyShown = conflicts.hasConflict();

		int limit = limits.perRollingDay(talentId);
		UUID id = UUID.randomUUID();
		return transaction.execute(status -> {
			lock.lock(talentId);
			if (store.countSubmissionsSince(talentId, now.minus(WINDOW)) >= limit) {
				throw new ApplyLimitReachedException(limit);
			}
			Submitted submitted = store.submit(id, gigId, talentId, gig.posterId(), note, busyShown, now)
					.orElseThrow(AlreadyAppliedException::new);
			store.logSubmission(talentId, submitted.id(), now);
			events.publishEvent(new ApplicationSubmitted(submitted.id(), gigId, talentId, gig.posterId(),
					!submitted.created(), now));
			return new Applied(submitted.id(), gigId, now, busyShown, !submitted.created());
		});
	}

	/** Trims the note; blank is no note. Contact details are shared only through the reveal flow (FR-26). */
	private String note(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String note = raw.strip();
		if (note.codePointCount(0, note.length()) > properties.noteMaxLength()) {
			throw new InvalidApplicationException("The note can be at most " + properties.noteMaxLength() + " characters.");
		}
		if (ContactDetails.appearsIn(note)) {
			throw new InvalidApplicationException("Leave phone numbers, email addresses and chat links out of the note; "
					+ "contact details are shared only after you're shortlisted.");
		}
		return note;
	}

}
