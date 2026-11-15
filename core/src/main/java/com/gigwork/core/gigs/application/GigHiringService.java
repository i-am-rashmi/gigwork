package com.gigwork.core.gigs.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.gigwork.core.gigs.GigExpired;
import com.gigwork.core.gigs.GigHiring;
import com.gigwork.core.gigs.persistence.GigStore;

/** What Hiring may change about a gig; every call is one conditional statement in the caller's transaction. */
@Service
class GigHiringService implements GigHiring {

	private final GigStore store;
	private final ApplicationEventPublisher events;

	GigHiringService(GigStore store, ApplicationEventPublisher events) {
		this.store = store;
		this.events = events;
	}

	@Override
	public boolean holdForHire(UUID gigId, Instant now) {
		return store.holdForHire(gigId, now);
	}

	@Override
	public boolean releaseHold(UUID gigId) {
		return store.releaseHold(gigId);
	}

	@Override
	public boolean fill(UUID gigId, Instant now) {
		return store.fill(gigId, now);
	}

	@Override
	public Outcome reopenOrExpire(UUID gigId, Instant now) {
		return store.reopenOrExpire(gigId, now).map(r -> {
			if (r.expired()) {
				events.publishEvent(new GigExpired(gigId, r.posterId(), now));
				return Outcome.EXPIRED;
			}
			return Outcome.REOPENED;
		}).orElse(Outcome.NOT_FILLED);
	}

}
