package com.gigwork.core.hiring.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.hiring.HiringApiSupport;
import com.gigwork.core.hiring.domain.AlreadyAppliedException;
import com.gigwork.core.hiring.domain.ApplyLimitReachedException;
import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/**
 * The invariants of applying under simultaneous requests (ADR-0002, ADR-0008): one application per Talent per gig,
 * and no more submissions in 24 hours than the limit. The limit is lowered to 5 so the test needs few gigs.
 */
@IdentityIntegrationTest
@TestPropertySource(properties = "gigwork.hiring.apply-submissions-per-day=5")
class ApplyConcurrencyTest {

	static final int LIMIT = 5;

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

	HiringApiSupport api;
	TestAccount poster;
	TestAccount talent;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		poster = accounts.create();
		talent = api.talent("Asha", "photographer");
	}

	/** The outcome of one apply: the exception's simple name, or "ok". */
	private String tryApply(UUID talentId, UUID gigId) {
		try {
			applying.apply(talentId, gigId, null, false);
			return "ok";
		}
		catch (RuntimeException e) {
			return e.getClass().getSimpleName();
		}
	}

	private int count(String sql, UUID talentId) {
		return jdbc.sql(sql).param("t", talentId).query(Integer.class).single();
	}

	@Test
	void theSameTalentApplyingTwiceToOneGigInParallelGetsOneApplicationAndOneRefusal() throws Exception {
		UUID gig = api.openGig(poster);
		List<Callable<String>> tasks = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			tasks.add(() -> tryApply(talent.id(), gig));
		}

		List<String> results = Concurrently.runAll(tasks);

		assertThat(results).containsExactlyInAnyOrder("ok", AlreadyAppliedException.class.getSimpleName());
		assertThat(count("SELECT count(*) FROM hiring.application WHERE talent_id = :t AND status = 'ACTIVE'", talent.id())).isEqualTo(1);
		assertThat(count("SELECT count(*) FROM hiring.application_submission WHERE talent_id = :t", talent.id())).isEqualTo(1);
	}

	@Test
	void manyParallelAppliesToOneGigStillCountOnce() throws Exception {
		UUID gig = api.openGig(poster);
		List<Callable<String>> tasks = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			tasks.add(() -> tryApply(talent.id(), gig));
		}

		List<String> results = Concurrently.runAll(tasks);

		assertThat(results.stream().filter("ok"::equals)).hasSize(1);
		assertThat(results.stream().filter(AlreadyAppliedException.class.getSimpleName()::equals)).hasSize(7);
		assertThat(count("SELECT count(*) FROM hiring.application_submission WHERE talent_id = :t", talent.id())).isEqualTo(1);
	}

	@Test
	void parallelAppliesToDifferentGigsSucceedExactlyUpToTheLimit() throws Exception {
		List<Callable<String>> tasks = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			UUID gig = api.openGig(poster);
			tasks.add(() -> tryApply(talent.id(), gig));
		}

		List<String> results = Concurrently.runAll(tasks);

		assertThat(results.stream().filter("ok"::equals)).hasSize(LIMIT);
		assertThat(results.stream().filter(ApplyLimitReachedException.class.getSimpleName()::equals)).hasSize(12 - LIMIT);
		assertThat(count("SELECT count(*) FROM hiring.application_submission WHERE talent_id = :t", talent.id())).isEqualTo(LIMIT);
		assertThat(count("SELECT count(*) FROM hiring.application WHERE talent_id = :t", talent.id())).isEqualTo(LIMIT);
	}

	@Test
	void theLimitOfOneTalentDoesNotHoldBackAnother() throws Exception {
		TestAccount other = api.talent("Bela", "photographer");
		List<Callable<String>> tasks = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			UUID gig = api.openGig(poster);
			tasks.add(() -> tryApply(talent.id(), gig));
			tasks.add(() -> tryApply(other.id(), gig));
		}

		Concurrently.runAll(tasks);

		assertThat(count("SELECT count(*) FROM hiring.application_submission WHERE talent_id = :t", talent.id())).isEqualTo(LIMIT);
		assertThat(count("SELECT count(*) FROM hiring.application_submission WHERE talent_id = :t", other.id())).isEqualTo(LIMIT);
	}

}
