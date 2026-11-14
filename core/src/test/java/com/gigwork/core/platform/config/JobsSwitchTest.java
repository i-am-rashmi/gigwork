package com.gigwork.core.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.gigwork.core.platform.ConditionalOnJobsEnabled;
import org.springframework.test.context.TestPropertySource;

import com.gigwork.core.TestcontainersConfiguration;
import com.gigwork.core.identity.support.Concurrently;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

/** {@code gigwork.jobs.enabled} (ADR-0010): off in tests, on by default, and the lock table lives in {@code platform}. */
class JobsSwitchTest {

	/** Stands for a module's job class: found by component scan only when the jobs are switched on. */
	@Component
	@ConditionalOnJobsEnabled
	static class ProbeJob {

	}

	@Nested
	@SpringBootTest
	@Import(TestcontainersConfiguration.class)
	class OffAsInTheTests {

		@Autowired
		ApplicationContext context;

		@Test
		void noSchedulerAndNoLockProviderExist() {
			assertThat(context.getBeanNamesForType(LockProvider.class)).isEmpty();
		}

		@Test
		void aClassMarkedAsAJobIsNotCreated() {
			assertThat(context.getBeanNamesForType(ProbeJob.class)).isEmpty();
		}

	}

	@Nested
	@SpringBootTest
	@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
	@TestPropertySource(properties = "gigwork.jobs.enabled=true")
	@Import(TestcontainersConfiguration.class)
	class On {

		@Autowired
		ApplicationContext context;

		@Autowired
		LockProvider lockProvider;

		@Autowired
		JdbcTemplate jdbc;

		@Test
		void aClassMarkedAsAJobIsCreatedAndTheLockTableIsInThePlatformSchema() {
			assertThat(context.getBeanNamesForType(ProbeJob.class)).hasSize(1);
			assertThat(jdbc.queryForObject(
					"SELECT count(*) FROM information_schema.tables WHERE table_schema = 'platform' AND table_name = 'shedlock'",
					Integer.class)).isEqualTo(1);
		}

		@Test
		void aLockHeldByOneRunCannotBeTakenByAnotherUntilItIsReleased() {
			LockConfiguration config = new LockConfiguration(java.time.Instant.now(), "switch-test-held",
					Duration.ofMinutes(5), Duration.ZERO);

			Optional<SimpleLock> first = lockProvider.lock(config);
			assertThat(first).isPresent();
			assertThat(lockProvider.lock(config)).isEmpty();

			first.get().unlock();
			Optional<SimpleLock> again = lockProvider.lock(config);
			assertThat(again).isPresent();
			again.get().unlock();
		}

		@Test
		void ofManySimultaneousRunsOnlyOneGetsTheLock() throws Exception {
			LockConfiguration config = new LockConfiguration(java.time.Instant.now(), "switch-test-race",
					Duration.ofMinutes(5), Duration.ZERO);

			List<Boolean> won = Concurrently.runAll(8, () -> lockProvider.lock(config).isPresent());

			assertThat(won).filteredOn(Boolean::booleanValue).hasSize(1);
			jdbc.update("DELETE FROM platform.shedlock WHERE name = 'switch-test-race'");
		}

	}

}
