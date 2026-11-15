package com.gigwork.core.platform.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.stereotype.Service;

import com.gigwork.core.platform.EventResubmission;
import com.gigwork.core.platform.JobRuns;
import com.gigwork.core.platform.config.EventResubmissionProperties;

/**
 * Resubmits incomplete event publications older than the configured age through Spring Modulith's registry. The job
 * class only adds the schedule and the lock, so tests run exactly this. Two instances resubmitting at once may deliver
 * an event twice; listeners are idempotent, so that changes nothing.
 */
@Service
class IncompleteEventResubmission implements EventResubmission {

	static final String JOB = "event-resubmission";

	private final IncompleteEventPublications incomplete;
	private final EventResubmissionProperties properties;
	private final JdbcClient jdbc;
	private final JobRuns jobs;
	private final Clock clock;

	IncompleteEventResubmission(IncompleteEventPublications incomplete, EventResubmissionProperties properties, JdbcClient jdbc,
			JobRuns jobs, Clock clock) {
		this.incomplete = incomplete;
		this.properties = properties;
		this.jdbc = jdbc;
		this.jobs = jobs;
		this.clock = clock;
	}

	@Override
	public long run() {
		return jobs.record(JOB, () -> {
			Instant cutoff = clock.instant().minus(properties.resubmitOlderThan());
			// the registry table belongs to Spring Modulith, which Platform wraps; this only counts what is about to be retried
			long due = jdbc.sql("SELECT count(*) FROM event_publication WHERE completion_date IS NULL AND publication_date <= :cutoff")
					.param("cutoff", java.time.OffsetDateTime.ofInstant(cutoff, java.time.ZoneOffset.UTC)).query(Long.class).single();
			incomplete.resubmitIncompletePublicationsOlderThan(properties.resubmitOlderThan());
			return due;
		});
	}

}
