package com.gigwork.core.hiring.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.availability.Availability;
import com.gigwork.core.availability.BookingConflictException;
import com.gigwork.core.gigs.GigHiring;
import com.gigwork.core.gigs.GigLookup;
import com.gigwork.core.gigs.GigState;
import com.gigwork.core.gigs.GigSummary;
import com.gigwork.core.hiring.HireConfirmed;
import com.gigwork.core.hiring.HireCancelled;
import com.gigwork.core.hiring.HireDeclined;
import com.gigwork.core.hiring.HireSide;
import com.gigwork.core.hiring.HireStarted;
import com.gigwork.core.hiring.HireWithdrawn;
import com.gigwork.core.hiring.config.HiringProperties;
import com.gigwork.core.hiring.domain.ApplicationNotActiveException;
import com.gigwork.core.hiring.domain.ApplicationNotFoundException;
import com.gigwork.core.hiring.domain.GigNotOpenException;
import com.gigwork.core.hiring.domain.HireAlreadyPendingException;
import com.gigwork.core.hiring.domain.HireBookingConflictException;
import com.gigwork.core.hiring.domain.HireNotConfirmedException;
import com.gigwork.core.hiring.domain.HireNotPendingException;
import com.gigwork.core.hiring.domain.InvalidApplicationException;
import com.gigwork.core.hiring.domain.HireWrongSideException;
import com.gigwork.core.hiring.domain.NotShortlistedException;
import com.gigwork.core.hiring.persistence.ApplicationStore;
import com.gigwork.core.hiring.persistence.HireStore;
import com.gigwork.core.hiring.persistence.ShortlistStore;
import com.gigwork.core.hiring.persistence.StoredApplication;
import com.gigwork.core.hiring.persistence.StoredHire;

/**
 * Starting, confirming, declining and withdrawing a hire (FR-27, FR-28, FR-29).
 *
 * <p>Row locks are always taken in the order application, hire, gig, booking, so two requests can never wait for
 * each other. Starting locks the application row FOR SHARE (a withdrawal needs FOR UPDATE, so it waits, and then
 * refuses because a hire exists), inserts the hire, and only then holds the gig. Confirming is one transaction:
 * hire PENDING to CONFIRMED (checking the expiry itself, never trusting the lapse job), gig OPEN to FILLED, the
 * booking, and the booking's ID on the hire. If any step is refused everything rolls back: the hire stays pending,
 * the gig open and held, and nothing is booked.
 *
 * <p>If both sides start a hire for the same application at the same moment, the second start finds the first
 * one's pending hire, and counts as confirming it.
 */
@Service
public class HireService {

	private static final Duration PENDING_FOR = Duration.ofHours(24);

	private final ApplicationStore applications;
	private final HireStore hires;
	private final ShortlistStore shortlists;
	private final GigLookup gigs;
	private final GigHiring gigHiring;
	private final Availability availability;
	private final TransactionTemplate transaction;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	HireService(ApplicationStore applications, HireStore hires, ShortlistStore shortlists, GigLookup gigs, GigHiring gigHiring,
			Availability availability, TransactionTemplate transaction, ApplicationEventPublisher events, Clock clock) {
		this.applications = applications;
		this.hires = hires;
		this.shortlists = shortlists;
		this.gigs = gigs;
		this.gigHiring = gigHiring;
		this.availability = availability;
		this.transaction = transaction;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * Starts a hire for a shortlisted applicant: the Poster marks them hired, or the Talent says "I've been hired for
	 * this". Returns the new pending hire, or the confirmed one if this start confirmed the other side's.
	 *
	 * @throws ApplicationNotFoundException the caller is neither the gig's Poster nor the applicant
	 * @throws ApplicationNotActiveException the application was withdrawn
	 * @throws NotShortlistedException the Talent is not currently shortlisted
	 * @throws GigNotOpenException the gig is not open or has started
	 * @throws HireAlreadyPendingException the gig already has a pending hire
	 * @throws HireBookingConflictException this start confirmed the other side's and the Talent is booked then
	 */
	public StoredHire start(UUID callerId, UUID applicationId) {
		StoredApplication application = applications.find(applicationId)
				.filter(a -> a.posterId().equals(callerId) || a.talentId().equals(callerId))
				.orElseThrow(ApplicationNotFoundException::new);
		HireSide side = application.posterId().equals(callerId) ? HireSide.POSTER : HireSide.TALENT;
		Instant now = clock.instant();
		GigSummary gig = openGig(application.gigId(), now);
		UUID id = UUID.randomUUID();
		Instant expiresAt = min(now.plus(PENDING_FOR), gig.startAt());
		return transaction.execute(status -> {
			StoredApplication locked = applications.lockForShare(applicationId).filter(StoredApplication::isActive)
					.orElseThrow(ApplicationNotActiveException::new);
			shortlists.find(applicationId).filter(ShortlistStore.Entry::isCurrent).orElseThrow(NotShortlistedException::new);
			if (!hires.insertPending(id, locked.gigId(), applicationId, locked.talentId(), locked.posterId(), side, now, expiresAt)) {
				StoredHire pending = hires.findPendingOfGig(locked.gigId()).orElseThrow(HireAlreadyPendingException::new);
				if (pending.applicationId().equals(applicationId) && pending.startedBy() != side) {
					return confirmPending(pending, now); // both sides started: the second start is the confirmation
				}
				throw new HireAlreadyPendingException();
			}
			if (!gigHiring.holdForHire(locked.gigId(), now)) {
				throw new GigNotOpenException();
			}
			// the gig is now locked by this transaction; if it was edited just before, its start may be earlier than assumed
			gigs.find(locked.gigId()).ifPresent(g -> hires.capExpiry(id, g.startAt()));
			StoredHire started = hires.find(id).orElseThrow();
			events.publishEvent(new HireStarted(id, applicationId, locked.gigId(), locked.talentId(), locked.posterId(), side,
					started.expiresAt(), now));
			return started;
		});
	}

	/**
	 * The side that did not start the hire confirms it.
	 *
	 * @throws ApplicationNotFoundException unknown hire, or the caller is neither side
	 * @throws HireWrongSideException the caller started it
	 * @throws HireNotPendingException it is no longer pending, or it expired
	 * @throws GigNotOpenException the gig is no longer open
	 * @throws HireBookingConflictException the Talent is booked at that time
	 */
	public StoredHire confirm(UUID callerId, UUID hireId) {
		StoredHire hire = visible(callerId, hireId);
		if (hire.startedBy() == side(hire, callerId)) {
			throw new HireWrongSideException("You started this hire; the other side confirms it. You can withdraw it instead.");
		}
		Instant now = clock.instant();
		return transaction.execute(status -> confirmPending(hire, now));
	}

	/**
	 * The side that did not start a pending hire declines it straight away; the gig stays open.
	 *
	 * @throws HireWrongSideException the caller started it
	 * @throws HireNotPendingException it is no longer pending
	 */
	public StoredHire decline(UUID callerId, UUID hireId) {
		StoredHire hire = visible(callerId, hireId);
		if (hire.startedBy() == side(hire, callerId)) {
			throw new HireWrongSideException("You started this hire; the other side can decline it. You can withdraw it instead.");
		}
		return endPending(hire, "DECLINED");
	}

	/**
	 * The side that started a pending hire withdraws it; the gig stays open.
	 *
	 * @throws HireWrongSideException the caller did not start it
	 * @throws HireNotPendingException it is no longer pending
	 */
	public StoredHire withdraw(UUID callerId, UUID hireId) {
		StoredHire hire = visible(callerId, hireId);
		if (hire.startedBy() != side(hire, callerId)) {
			throw new HireWrongSideException("The other side started this hire. You can decline it instead.");
		}
		return endPending(hire, "WITHDRAWN");
	}

	/**
	 * Either side cancels a confirmed hire, giving a reason (FR-27a). In one transaction: the hire is cancelled, the gig
	 * reopens if it has not started (otherwise it expires) and the Talent's booking is released, so the Poster can hire
	 * someone else straight away.
	 *
	 * @throws InvalidApplicationException the reason is missing or longer than 500 characters
	 * @throws ApplicationNotFoundException unknown hire, or the caller is neither side
	 * @throws HireNotConfirmedException the hire is not confirmed
	 */
	public StoredHire cancel(UUID callerId, UUID hireId, String rawReason) {
		StoredHire hire = visible(callerId, hireId);
		String reason = rawReason == null ? "" : rawReason.strip();
		if (reason.isEmpty() || reason.codePointCount(0, reason.length()) > 500) {
			throw new InvalidApplicationException("Give a reason of 1 to 500 characters for cancelling the hire.");
		}
		HireSide side = side(hire, callerId);
		Instant now = clock.instant();
		return transaction.execute(status -> {
			StoredHire cancelled = hires.cancel(hireId, side, reason, now).orElseThrow(HireNotConfirmedException::new);
			gigHiring.reopenOrExpire(cancelled.gigId(), now);
			availability.release(cancelled.gigId());
			events.publishEvent(new HireCancelled(cancelled.id(), cancelled.applicationId(), cancelled.gigId(),
					cancelled.talentId(), cancelled.posterId(), side, reason, now));
			return cancelled;
		});
	}

	/** One hire, for either of its two sides; anyone else gets a 404. */
	public StoredHire hire(UUID callerId, UUID hireId) {
		return visible(callerId, hireId);
	}

	// ---- the steps ----------------------------------------------------------------------------------------------------------

	/** Inside the caller's transaction: confirm, fill, book. Any refusal throws and rolls everything back. */
	private StoredHire confirmPending(StoredHire hire, Instant now) {
		if (!hires.confirm(hire.id(), now)) {
			throw new HireNotPendingException();
		}
		if (!gigHiring.fill(hire.gigId(), now)) {
			throw new GigNotOpenException();
		}
		GigSummary gig = gigs.find(hire.gigId()).orElseThrow(GigNotOpenException::new);
		UUID bookingId;
		try {
			bookingId = availability.book(hire.talentId(), hire.gigId(), gig.startAt(), gig.endAt());
		}
		catch (BookingConflictException e) {
			throw new HireBookingConflictException();
		}
		hires.setBooking(hire.id(), bookingId);
		events.publishEvent(new HireConfirmed(hire.id(), hire.applicationId(), hire.gigId(), hire.talentId(), hire.posterId(),
				bookingId, now));
		return hires.find(hire.id()).orElseThrow();
	}

	private StoredHire endPending(StoredHire hire, String toStatus) {
		Instant now = clock.instant();
		return transaction.execute(status -> {
			StoredHire ended = hires.endPending(hire.id(), toStatus, now).orElseThrow(HireNotPendingException::new);
			gigHiring.releaseHold(ended.gigId());
			if ("DECLINED".equals(toStatus)) {
				events.publishEvent(new HireDeclined(ended.id(), ended.applicationId(), ended.gigId(), ended.talentId(),
						ended.posterId(), ended.startedBy(), now));
			}
			else {
				events.publishEvent(new HireWithdrawn(ended.id(), ended.applicationId(), ended.gigId(), ended.talentId(),
						ended.posterId(), ended.startedBy(), now));
			}
			return ended;
		});
	}

	private StoredHire visible(UUID callerId, UUID hireId) {
		return hires.find(hireId).filter(h -> h.sideOf(callerId).isPresent()).orElseThrow(ApplicationNotFoundException::new);
	}

	private static HireSide side(StoredHire hire, UUID callerId) {
		return hire.sideOf(callerId).orElseThrow(ApplicationNotFoundException::new);
	}

	private GigSummary openGig(UUID gigId, Instant now) {
		return gigs.find(gigId).filter(g -> g.status() == GigState.OPEN && g.startAt().isAfter(now))
				.orElseThrow(GigNotOpenException::new);
	}

	private static Instant min(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

}
