package com.gigwork.core.hiring.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.hiring.HiringApiSupport;
import com.gigwork.core.hiring.persistence.ShortlistStore;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/**
 * The accepted race of a view (FR-26), made certain: a consent withdrawal that arrives while a view holds the
 * shortlist entry waits until the view has been logged. So a logged view always saw a consent that existed, and the
 * withdrawal takes effect straight after.
 */
@IdentityIntegrationTest
class ContactViewLockTest {

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
	ShortlistStore store;
	@Autowired
	ShortlistService shortlists;
	@Autowired
	TransactionTemplate transaction;

	@Test
	void aConsentWithdrawalWaitsForAViewInProgressAndThenEndsAccess() throws Exception {
		clock.reset();
		HiringApiSupport api = new HiringApiSupport(mvc, reference, accounts, clock);
		TestAccount poster = accounts.create();
		TestAccount talent = api.talent("Asha", "photographer");
		api.sharesContact(talent);
		UUID application = api.applyOk(talent, api.openGig(poster));
		api.shortlist(poster, application, false).andExpect(status().isOk());
		api.consent(talent, application, true).andExpect(status().isOk());

		CompletableFuture<Boolean> withdrawal = new CompletableFuture<>();
		transaction.executeWithoutResult(tx -> {
			var entry = store.findForView(application).orElseThrow(); // the view has read the entry: consent is there
			assertThat(entry.talentConsents()).isTrue();
			CompletableFuture.runAsync(() -> withdrawal.complete(shortlists.setConsent(talent.id(), application, false)));
			stillWaiting(withdrawal);
			store.logView(poster.id(), talent.id(), entry.gigId(), application, clock.instant());
		});

		assertThat(withdrawal.get(10, TimeUnit.SECONDS)).isFalse(); // consent withdrawn once the view was logged
		assertThat(jdbc.sql("SELECT count(*) FROM hiring.contact_access_log WHERE application_id = :a").param("a", application)
				.query(Integer.class).single()).isEqualTo(1);
		api.talentContact(poster, application).andExpect(status().isConflict());
	}

	private static void stillWaiting(CompletableFuture<Boolean> future) {
		try {
			future.get(1, TimeUnit.SECONDS);
			throw new AssertionError("the withdrawal did not wait for the view");
		}
		catch (TimeoutException expected) {
			// waiting, as it should
		}
		catch (java.util.concurrent.ExecutionException | InterruptedException e) {
			throw new AssertionError(e);
		}
	}

}
