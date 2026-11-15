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
 * Proof that the locks are what hold the shortlist limits: with them taken away (no-ops), the parallel shortlists
 * that {@link ShortlistConcurrencyTest} shows stopping at 3 per gig all get through, because each one counts before
 * any other has committed. If this test ever fails, the other one no longer proves anything.
 */
@IdentityIntegrationTest
@TestPropertySource(properties = { "gigwork.hiring.shortlist-per-gig=3", "gigwork.hiring.shortlist-actions-per-day=50" })
class ShortlistLimitNeedsTheLockTest {

	@MockitoBean
	ShortlistLocks locks; // does nothing: the locks are gone

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
	ShortlistService shortlists;

	@Test
	void withoutTheLocksParallelShortlistsGoPastTheLimit() throws Exception {
		clock.reset();
		HiringApiSupport api = new HiringApiSupport(mvc, reference, accounts, clock);
		TestAccount poster = accounts.create();
		UUID gig = api.openGig(poster);
		List<Callable<Boolean>> tasks = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			UUID application = api.applyOk(api.talent("T" + i, "photographer"), gig);
			tasks.add(() -> {
				try {
					shortlists.shortlist(poster.id(), application, false);
					return true;
				}
				catch (RuntimeException e) {
					return false;
				}
			});
		}

		Concurrently.runAll(tasks);

		assertThat(jdbc.sql("SELECT count(*) FROM hiring.shortlist_entry WHERE gig_id = :g AND removed_at IS NULL").param("g", gig)
				.query(Integer.class).single()).as("shortlisted although the limit is 3").isGreaterThan(3);
	}

}
