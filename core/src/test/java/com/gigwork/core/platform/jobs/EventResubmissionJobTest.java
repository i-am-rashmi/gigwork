package com.gigwork.core.platform.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import com.gigwork.core.TestcontainersConfiguration;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * The event resubmission job is really scheduled when jobs are on: every 5 minutes, behind a lock named for it, with a lock that
 * lasts a little less than the interval and far longer than a run. (What it does is in hiring's GigCancelledRetryTest.) The
 * context is closed afterwards so that no scheduler keeps running against the database of the tests that follow.
 */
@SpringBootTest
@TestPropertySource(properties = "gigwork.jobs.enabled=true")
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventResubmissionJobTest {

	@Autowired
	ApplicationContext context;
	@Autowired
	ScheduledAnnotationBeanPostProcessor scheduler;

	private Method job() throws Exception {
		return EventResubmissionJob.class.getDeclaredMethod("resubmitIncompleteEvents");
	}

	@Test
	void theJobIsAComponentOnlyWhenJobsAreEnabledAndIsScheduledEveryMinute() throws Exception {
		assertThat(context.getBeansOfType(EventResubmissionJob.class)).hasSize(1);
		List<CronTask> crons = scheduler.getScheduledTasks().stream().map(t -> t.getTask()).filter(CronTask.class::isInstance)
				.map(CronTask.class::cast).filter(t -> t.toString().contains("EventResubmissionJob")).toList();

		assertThat(crons).hasSize(1);
		assertThat(crons.get(0).getExpression()).isEqualTo("0 */5 * * * *");
		assertThat(AnnotatedElementUtils.findMergedAnnotation(job(), Scheduled.class).cron()).isEqualTo("0 */5 * * * *");
	}

	@Test
	void theLockHasANameAndLastsALittleLessThanTheIntervalButFarMoreThanARun() throws Exception {
		SchedulerLock lock = AnnotatedElementUtils.findMergedAnnotation(job(), SchedulerLock.class);

		assertThat(lock.name()).isEqualTo("platform.resubmit-incomplete-events");
		assertThat(Duration.parse(lock.lockAtLeastFor())).isLessThan(Duration.ofMinutes(5)).isGreaterThan(Duration.ofMinutes(3));
		assertThat(Duration.parse(lock.lockAtMostFor())).isGreaterThanOrEqualTo(Duration.ofMinutes(2));
	}

}
