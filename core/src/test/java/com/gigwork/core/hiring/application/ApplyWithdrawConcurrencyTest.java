package com.gigwork.core.hiring.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.hiring.HiringApiSupport;
import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/**
 * Applying again and withdrawing at the same moment. Either order is fine, but the result must be one of the two
 * consistent states: the application is ACTIVE exactly when the apply went through, and every state change that
 * happened has its submission row.
 */
@IdentityIntegrationTest
class ApplyWithdrawConcurrencyTest {

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
	@Autowired
	WithdrawService withdrawing;

	@Test
	void applyingAgainWhileWithdrawingEndsInAConsistentState() throws Exception {
		clock.reset();
		HiringApiSupport api = new HiringApiSupport(mvc, reference, accounts, clock);
		TestAccount poster = accounts.create();
		TestAccount talent = api.talent("Asha", "photographer");
		int appliedAgain = 0;
		int stayedWithdrawn = 0;
		for (int round = 0; round < 10; round++) {
			UUID gig = api.openGig(poster);
			UUID application = api.applyOk(talent, gig);

			List<Callable<Boolean>> tasks = List.of(() -> {
				try {
					applying.apply(talent.id(), gig, null, false);
					return true;
				}
				catch (RuntimeException e) {
					return false;
				}
			}, () -> {
				withdrawing.withdraw(talent.id(), application);
				return true;
			});
			boolean applyWentThrough = Concurrently.runAll(tasks).get(0);

			var row = jdbc.sql("SELECT status, withdrawn_at FROM hiring.application WHERE id = :id").param("id", application)
					.query().singleRow();
			int submissions = jdbc.sql("SELECT count(*) FROM hiring.application_submission WHERE application_id = :id")
					.param("id", application).query(Integer.class).single();
			if (applyWentThrough) { // the withdrawal came first, then the Talent applied again
				assertThat(row.get("status")).isEqualTo("ACTIVE");
				assertThat(row.get("withdrawn_at")).isNull();
				assertThat(submissions).isEqualTo(2);
				appliedAgain++;
			}
			else { // the apply found the application still active and was refused; the withdrawal then won
				assertThat(row.get("status")).isEqualTo("WITHDRAWN");
				assertThat(row.get("withdrawn_at")).isNotNull();
				assertThat(submissions).isEqualTo(1);
				stayedWithdrawn++;
			}
		}
		assertThat(appliedAgain + stayedWithdrawn).isEqualTo(10);
	}

}
