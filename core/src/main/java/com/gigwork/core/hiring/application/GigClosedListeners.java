package com.gigwork.core.hiring.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.gigwork.core.availability.Availability;
import com.gigwork.core.gigs.GigCancelled;
import com.gigwork.core.gigs.GigExpired;
import com.gigwork.core.hiring.HireCancelled;
import com.gigwork.core.hiring.HireLapsed;
import com.gigwork.core.hiring.HireSide;
import com.gigwork.core.hiring.HireWithdrawn;
import com.gigwork.core.hiring.persistence.HireStore;

/**
 * Hiring follows its gig being closed by someone else (FR-19, FR-27, FR-31). These handlers run after the gig's own
 * transaction, in their own, and are retried by the event resubmission job if they fail, so they are idempotent: each
 * is a conditional update that finds nothing to do the second time, and releasing a booking twice is harmless.
 *
 * <ul>
 * <li>The Poster cancelled the gig: a confirmed hire is cancelled by the Poster, with the gig's reason, and the
 * Talent's booking is released; a pending hire is withdrawn.
 * <li>The gig expired: a pending hire lapses.
 * </ul>
 *
 * <p>Until a handler has run, a confirm still cannot succeed on a closed gig (filling needs it open), so no hire can
 * be confirmed after the gig closed.
 */
@Component
class GigClosedListeners {

	private final HireStore hires;
	private final Availability availability;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	GigClosedListeners(HireStore hires, Availability availability, ApplicationEventPublisher events, Clock clock) {
		this.hires = hires;
		this.availability = availability;
		this.events = events;
		this.clock = clock;
	}

	@ApplicationModuleListener
	void on(GigCancelled event) {
		Instant now = clock.instant();
		hires.cancelConfirmedOfGig(event.gigId(), event.reason(), now).ifPresent(hire -> {
			availability.release(event.gigId());
			events.publishEvent(new HireCancelled(hire.id(), hire.applicationId(), hire.gigId(), hire.talentId(),
					hire.posterId(), HireSide.POSTER, event.reason(), now));
		});
		hires.endPendingOfGig(event.gigId(), "WITHDRAWN", now).ifPresent(hire -> events.publishEvent(new HireWithdrawn(hire.id(),
				hire.applicationId(), hire.gigId(), hire.talentId(), hire.posterId(), hire.startedBy(), now)));
		// a booking that outlived its hire (a retry after a partial failure) is released too: idempotent
		if (event.wasFilled()) {
			availability.release(event.gigId());
		}
	}

	@ApplicationModuleListener
	void on(GigExpired event) {
		Instant now = clock.instant();
		hires.endPendingOfGig(event.gigId(), "LAPSED", now).ifPresent(hire -> events.publishEvent(new HireLapsed(hire.id(),
				hire.applicationId(), hire.gigId(), hire.talentId(), hire.posterId(), hire.startedBy(), now)));
	}

}
