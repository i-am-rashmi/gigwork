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
import com.gigwork.core.hiring.domain.ShortlistActionLimitException;
import com.gigwork.core.hiring.domain.ShortlistFullException;
import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/**
 * The shortlist limits under simultaneous requests (NFR-38, ADR-0002): at most N currently shortlisted per gig and
 * M shortlist actions per Poster per day, however many requests arrive at once. The limits are lowered (3 and 5) so
 * the tests need few Talent.
 */
@IdentityIntegrationTest
@TestPropertySource(properties = { "gigwork.hiring.shortlist-per-gig=3", "gigwork.hiring.shortlist-actions-per-day=5" })
class ShortlistConcurrencyTest {

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
	@Autowired
	WithdrawService withdrawing;

	HiringApiSupport api;
	TestAccount poster;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		poster = accounts.create();
	}

	private String tryShortlist(UUID application) {
		try {
			shortlists.shortlist(poster.id(), application, false);
			return "ok";
		}
		catch (RuntimeException e) {
			return e.getClass().getSimpleName();
		}
	}

	@Test
	void parallelShortlistsOfOneGigStopAtTheLimit() throws Exception {
		UUID gig = api.openGig(poster);
		List<Callable<String>> tasks = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			UUID application = api.applyOk(api.talent("T" + i, "photographer"), gig);
			tasks.add(() -> tryShortlist(application));
		}

		List<String> results = Concurrently.runAll(tasks);

		assertThat(results.stream().filter("ok"::equals)).hasSize(3);
		assertThat(results.stream().filter(ShortlistFullException.class.getSimpleName()::equals)).hasSize(5);
		assertThat(jdbc.sql("SELECT count(*) FROM hiring.shortlist_entry WHERE gig_id = :g AND removed_at IS NULL").param("g", gig)
				.query(Integer.class).single()).isEqualTo(3);
		assertThat(jdbc.sql("SELECT count(*) FROM hiring.shortlist_action WHERE poster_id = :p").param("p", poster.id())
				.query(Integer.class).single()).isEqualTo(3);
	}

	@Test
	void parallelShortlistsAcrossGigsStopAtThePostersDailyLimit() throws Exception {
		List<Callable<String>> tasks = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			UUID application = api.applyOk(api.talent("T" + i, "photographer"), api.openGig(poster));
			tasks.add(() -> tryShortlist(application));
		}

		List<String> results = Concurrently.runAll(tasks);

		assertThat(results.stream().filter("ok"::equals)).hasSize(5);
		assertThat(results.stream().filter(ShortlistActionLimitException.class.getSimpleName()::equals)).hasSize(4);
		assertThat(jdbc.sql("SELECT count(*) FROM hiring.shortlist_action WHERE poster_id = :p").param("p", poster.id())
				.query(Integer.class).single()).isEqualTo(5);
	}

	@Test
	void parallelRemovalsAndShortlistsNeverGoPastTheDailyLimit() throws Exception {
		UUID gig = api.openGig(poster);
		List<UUID> applications = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			applications.add(api.applyOk(api.talent("T" + i, "photographer"), gig));
			shortlists.shortlist(poster.id(), applications.get(i), false); // 3 actions used
		}
		List<Callable<String>> tasks = new ArrayList<>();
		for (UUID application : applications) { // 3 removals (always allowed) race with 4 more shortlists
			tasks.add(() -> {
				try {
					shortlists.remove(poster.id(), application);
					return "removed";
				}
				catch (RuntimeException e) {
					return e.getClass().getSimpleName();
				}
			});
		}
		for (int i = 0; i < 4; i++) {
			UUID application = api.applyOk(api.talent("N" + i, "photographer"), gig);
			tasks.add(() -> tryShortlist(application));
		}

		Concurrently.runAll(tasks);

		int actions = jdbc.sql("SELECT count(*) FROM hiring.shortlist_action WHERE poster_id = :p").param("p", poster.id())
				.query(Integer.class).single();
		long adds = jdbc.sql("SELECT count(*) FROM hiring.shortlist_action WHERE poster_id = :p AND action = 'SHORTLIST'").param("p", poster.id())
				.query(Long.class).single();
		long removes = actions - adds;
		assertThat(removes).isEqualTo(3); // removals are never refused
		assertThat(adds).isLessThanOrEqualTo(5L); // the additions stopped at the limit counting every action before them
		assertThat(jdbc.sql("SELECT count(*) FROM hiring.shortlist_entry WHERE gig_id = :g AND removed_at IS NULL").param("g", gig)
				.query(Integer.class).single()).isLessThanOrEqualTo(3);
	}

	@Test
	void shortlistingWhileTheApplicationIsWithdrawnLeavesNoLiveShortlistOnAWithdrawnApplication() throws Exception {
		UUID gig = api.openGig(poster);
		for (int round = 0; round < 3; round++) {
			TestAccount talent = api.talent("W" + round, "photographer");
			UUID application = api.applyOk(talent, gig);

			List<Callable<Boolean>> tasks = List.of(() -> "ok".equals(tryShortlist(application)), () -> {
				withdrawing.withdraw(talent.id(), application);
				return true;
			});
			boolean shortlisted = Concurrently.runAll(tasks).get(0);

			var row = jdbc.sql("""
					SELECT a.status, e.application_id IS NOT NULL AS has_entry, e.removed_at IS NULL AS current
					FROM hiring.application a LEFT JOIN hiring.shortlist_entry e ON e.application_id = a.id
					WHERE a.id = :id""").param("id", application).query().singleRow();
			assertThat(row.get("status")).isEqualTo("WITHDRAWN");
			// either the shortlist went first and was then ended by the withdrawal, or it was refused: never live
			assertThat(Boolean.TRUE.equals(row.get("has_entry")) && Boolean.TRUE.equals(row.get("current"))).isFalse();
			assertThat(row.get("has_entry")).isEqualTo(shortlisted);
		}
	}

}
