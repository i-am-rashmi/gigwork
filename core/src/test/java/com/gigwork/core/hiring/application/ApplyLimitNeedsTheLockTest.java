package com.gigwork.core.hiring.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.hiring.HiringApiSupport;
import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/**
 * Proof that the per-Talent lock is what holds the limit: with the lock taken away (a no-op), the same parallel
 * applies that {@link ApplyConcurrencyTest} shows stopping at the limit all get through, because each one counts
 * before any of the others has written. If this test ever fails, the other one no longer proves anything.
 */
@IdentityIntegrationTest
@TestPropertySource(properties = "gigwork.hiring.apply-submissions-per-day=5")
class ApplyLimitNeedsTheLockTest {

	@MockitoBean
	TalentLock lock; // does nothing: the lock is gone

	@Autowired
	MockMvc mvc;
	@Autowired
	ReferenceData reference;
	@Autowired
	TestAccounts accounts;
	@Autowired
	MutableClock clock;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	ApplyService applying;

	@Test
	void withoutTheLockParallelAppliesGoPastTheLimit() throws Exception {
		clock.reset();
		HiringApiSupport api = new HiringApiSupport(mvc, reference, accounts, clock);
		TestAccount poster = accounts.create();
		TestAccount talent = api.talent("Asha", "photographer");
		List<Callable<Boolean>> tasks = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			UUID gig = api.openGig(poster);
			tasks.add(() -> {
				try {
					applying.apply(talent.id(), gig, null, false);
					return true;
				}
				catch (RuntimeException e) {
					return false;
				}
			});
		}

		List<Boolean> results = Concurrently.runAll(tasks);

		assertThat(results.stream().filter(Boolean::booleanValue).count()).as("applies that got past a limit of 5").isGreaterThan(5);
	}

}
