package com.gigwork.core.platform.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gigwork.core.platform.ConditionalOnJobsEnabled;
import com.gigwork.core.platform.EventResubmission;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Retries events whose listener did not complete, every 5 minutes (ADR-0010), so a failed listener (a gig cancelled
 * but its hire and booking still standing) is retried until it works. ShedLock makes one instance do it per tick; the
 * lock is an optimisation, since listeners are idempotent.
 */
@Component
@ConditionalOnJobsEnabled
class EventResubmissionJob {

	private final EventResubmission resubmission;

	EventResubmissionJob(EventResubmission resubmission) {
		this.resubmission = resubmission;
	}

	@Scheduled(cron = "0 */5 * * * *")
	@SchedulerLock(name = "platform.resubmit-incomplete-events", lockAtLeastFor = "PT4M", lockAtMostFor = "PT15M")
	void resubmitIncompleteEvents() {
		resubmission.run();
	}

}
