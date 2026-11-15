package com.gigwork.core.hiring.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.gigs.GigHiring;
import com.gigwork.core.hiring.HireLapsed;
import com.gigwork.core.hiring.config.HiringProperties;
import com.gigwork.core.hiring.persistence.HireStore;
import com.gigwork.core.platform.JobRuns;

/**
 * The work of the lapse job (FR-27, ADR-0010): a pending hire past its expiry becomes LAPSED and its gig is released
 * for editing and for another hire. The job class only adds the schedule and the lock, so tests run exactly this.
 *
 * <p>Correctness does not depend on the schedule or the lock: every batch is one conditional update, so running it
 * twice at the same time lapses each hire once and writes one {@link HireLapsed} for it, in the same transaction.
 * Confirming never relies on this having run: it checks the expiry itself.
 */
@Service
public class HireLapse {

	static final String JOB = "hire-lapse";

	private final HireStore hires;
	private final GigHiring gigHiring;
	private final HiringProperties properties;
	private final TransactionTemplate transaction;
	private final ApplicationEventPublisher events;
	private final JobRuns jobs;
	private final Clock clock;

	HireLapse(HireStore hires, GigHiring gigHiring, HiringProperties properties, TransactionTemplate transaction,
			ApplicationEventPublisher events, JobRuns jobs, Clock clock) {
		this.hires = hires;
		this.gigHiring = gigHiring;
		this.properties = properties;
		this.transaction = transaction;
		this.events = events;
		this.jobs = jobs;
		this.clock = clock;
	}

	/** One run: lapses every pending hire that is due, in batches, logs and records the rows changed and the duration. */
	public long run() {
		return jobs.record(JOB, this::lapseDue);
	}

	private long lapseDue() {
		int batch = properties.lapseBatchSize();
		long total = 0;
		int changed;
		do {
			Instant now = clock.instant();
			Integer rows = transaction.execute(status -> {
				var lapsed = hires.lapseDue(now, batch);
				lapsed.forEach(hire -> {
					gigHiring.releaseHold(hire.gigId());
					events.publishEvent(new HireLapsed(hire.id(), hire.applicationId(), hire.gigId(), hire.talentId(),
							hire.posterId(), hire.startedBy(), now));
				});
				return lapsed.size();
			});
			changed = rows == null ? 0 : rows;
			total += changed;
		}
		while (changed == batch);
		return total;
	}

}
