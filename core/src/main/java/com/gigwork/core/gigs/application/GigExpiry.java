package com.gigwork.core.gigs.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.gigs.GigExpired;
import com.gigwork.core.gigs.config.GigProperties;
import com.gigwork.core.gigs.persistence.GigStore;
import com.gigwork.core.platform.JobRuns;

/**
 * The work of the expiry job (FR-31, ADR-0010): an open gig whose start time has passed becomes EXPIRED. The job class
 * only adds the schedule and the lock, so tests run exactly this.
 *
 * <p>Correctness does not depend on the schedule or the lock: every batch is one conditional update, so running it
 * twice at the same time expires each gig once and writes one {@link GigExpired} for it, in the same transaction.
 * Requests never rely on this having run: editing already requires that the gig has not started.
 */
@Service
public class GigExpiry {

	static final String JOB = "gig-expiry";

	private final GigStore store;
	private final GigProperties properties;
	private final TransactionTemplate transaction;
	private final ApplicationEventPublisher events;
	private final JobRuns jobs;
	private final Clock clock;

	GigExpiry(GigStore store, GigProperties properties, TransactionTemplate transaction, ApplicationEventPublisher events,
			JobRuns jobs, Clock clock) {
		this.store = store;
		this.properties = properties;
		this.transaction = transaction;
		this.events = events;
		this.jobs = jobs;
		this.clock = clock;
	}

	/** One run: expires every gig that is due, in batches, logs and records the rows changed and the duration. */
	public long run() {
		return jobs.record(JOB, this::expireDue);
	}

	private long expireDue() {
		int batch = properties.expiryBatchSize();
		long total = 0;
		int changed;
		do {
			Instant now = clock.instant();
			Integer rows = transaction.execute(status -> {
				var expired = store.expireDue(now, batch);
				expired.forEach(gig -> events.publishEvent(new GigExpired(gig.id(), gig.posterId(), now)));
				return expired.size();
			});
			changed = rows == null ? 0 : rows;
			total += changed;
		}
		while (changed == batch);
		return total;
	}

}
