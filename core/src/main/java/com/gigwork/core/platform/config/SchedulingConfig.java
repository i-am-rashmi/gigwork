package com.gigwork.core.platform.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.gigwork.core.platform.ConditionalOnJobsEnabled;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * Scheduling for the whole core (ADR-0010). Modules declare their own {@code @Scheduled @SchedulerLock} methods in a
 * {@code jobs} package; Platform owns this configuration and the switch {@code gigwork.jobs.enabled}, which turns
 * every job off (the tests do, and call the job methods directly): job classes carry {@link ConditionalOnJobsEnabled}.
 *
 * <p>The lock is an optimisation so that one instance does the work per tick. {@code defaultLockAtMostFor} is far
 * above any run's expected duration, so a crashed instance cannot block a job for long; each job sets
 * {@code lockAtLeastFor} a little below its interval.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnJobsEnabled
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class SchedulingConfig {

	@Bean
	LockProvider lockProvider(DataSource dataSource) {
		return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
				.withJdbcTemplate(new JdbcTemplate(dataSource)).withTableName("platform.shedlock").usingDbTime().build());
	}

}
