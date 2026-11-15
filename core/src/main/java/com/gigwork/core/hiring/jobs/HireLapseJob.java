package com.gigwork.core.hiring.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gigwork.core.hiring.application.HireLapse;
import com.gigwork.core.platform.ConditionalOnJobsEnabled;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Lapses pending hires past their expiry, every minute (ADR-0010). ShedLock makes one instance do it per tick; the
 * lock is an optimisation, not what makes it correct. {@code lockAtLeastFor} is a little below the interval,
 * {@code lockAtMostFor} far above a run.
 */
@Component
@ConditionalOnJobsEnabled
class HireLapseJob {

	private final HireLapse lapse;

	HireLapseJob(HireLapse lapse) {
		this.lapse = lapse;
	}

	@Scheduled(cron = "0 * * * * *")
	@SchedulerLock(name = "hiring.lapse-pending-hires", lockAtLeastFor = "PT50S", lockAtMostFor = "PT5M")
	void lapsePendingHires() {
		lapse.run();
	}

}
