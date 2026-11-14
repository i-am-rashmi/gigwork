package com.gigwork.core.gigs.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gigwork.core.gigs.application.GigExpiry;
import com.gigwork.core.platform.ConditionalOnJobsEnabled;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Expires open gigs whose start has passed, every minute (ADR-0010). ShedLock makes one instance do it per tick; the
 * lock is an optimisation, not what makes it correct. {@code lockAtLeastFor} is a little below the interval so that
 * instances with slightly different clocks do not both run in one minute; {@code lockAtMostFor} is far above a run.
 */
@Component
@ConditionalOnJobsEnabled
class GigExpiryJob {

	private final GigExpiry expiry;

	GigExpiryJob(GigExpiry expiry) {
		this.expiry = expiry;
	}

	@Scheduled(cron = "0 * * * * *")
	@SchedulerLock(name = "gigs.expire-open-gigs", lockAtLeastFor = "PT50S", lockAtMostFor = "PT5M")
	void expireOpenGigs() {
		expiry.run();
	}

}
