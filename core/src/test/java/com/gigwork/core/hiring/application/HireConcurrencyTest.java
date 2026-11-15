package com.gigwork.core.hiring.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.hiring.HiringApiSupport;
import com.gigwork.core.hiring.HiringApiSupport.Shortlisted;
import com.gigwork.core.hiring.domain.HireBookingConflictException;
import com.gigwork.core.hiring.domain.HireInProgressException;
import com.gigwork.core.hiring.domain.HireNotPendingException;
import com.gigwork.core.hiring.persistence.StoredHire;
import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/**
 * The invariants of hiring under simultaneous requests (ADR-0002, ADR-0008, NFR-14): one confirmation per hire, one
 * hire per gig, no hire on a withdrawn application, no double booking of a Talent, and an expiry that never passes
 * the gig's start.
 */
@IdentityIntegrationTest
class HireConcurrencyTest {

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
	HireService hires;
	@Autowired
	WithdrawService withdrawing;
	@Autowired
	ShortlistService shortlists;

	HiringApiSupport api;
	TestAccount poster;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		poster = accounts.create();
	}

	private String run(Runnable work) {
		try {
			work.run();
			return "ok";
		}
		catch (RuntimeException e) {
			return e.getClass().getSimpleName();
		}
	}

	private int count(String sql, Object... params) {
		var statement = jdbc.sql(sql);
		for (int i = 0; i < params.length; i++) {
			statement = statement.param("p" + i, params[i]);
		}
		return statement.query(Integer.class).single();
	}

	private String gigStatus(UUID gig) {
		return jdbc.sql("SELECT status FROM gigs.gig WHERE id = :id").param("id", gig).query(String.class).single();
	}

	@Test
	void twoConfirmationsOfOneHireLetExactlyOneThrough() throws Exception {
		UUID gig = api.openGig(poster);
		Shortlisted s = api.shortlisted(poster, gig, "Asha");
		UUID hire = hires.start(poster.id(), s.application()).id();
		List<Callable<String>> tasks = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			tasks.add(() -> run(() -> hires.confirm(s.talent().id(), hire)));
		}

		List<String> results = Concurrently.runAll(tasks);

		assertThat(results.stream().filter("ok"::equals)).hasSize(1);
		assertThat(results.stream().filter(HireNotPendingException.class.getSimpleName()::equals)).hasSize(3);
		assertThat(count("SELECT count(*) FROM availability.booking WHERE gig_id = :p0 AND status = 'ACTIVE'", gig)).isEqualTo(1);
		assertThat(gigStatus(gig)).isEqualTo("FILLED");
	}

	@Test
	void bothSidesStartingAtOnceEndInOneConfirmedHire() throws Exception {
		for (int round = 0; round < 5; round++) {
			UUID gig = api.openGig(poster);
			Shortlisted s = api.shortlisted(poster, gig, "R" + round);

			List<Callable<String>> tasks = List.of(() -> run(() -> hires.start(poster.id(), s.application())),
					() -> run(() -> hires.start(s.talent().id(), s.application())));
			List<String> results = Concurrently.runAll(tasks);

			assertThat(results).containsOnly("ok");
			assertThat(count("SELECT count(*) FROM hiring.hire WHERE gig_id = :p0", gig)).isEqualTo(1);
			assertThat(count("SELECT count(*) FROM hiring.hire WHERE gig_id = :p0 AND status = 'CONFIRMED' AND booking_id IS NOT NULL", gig)).isEqualTo(1);
			assertThat(gigStatus(gig)).isEqualTo("FILLED");
			assertThat(count("SELECT count(*) FROM availability.booking WHERE gig_id = :p0 AND status = 'ACTIVE'", gig)).isEqualTo(1);
		}
	}

	@Test
	void oneTalentConfirmingTwoHiresForOverlappingGigsGetsExactlyOne() throws Exception {
		for (int round = 0; round < 3; round++) {
			UUID gigA = api.openGig(poster);
			UUID gigB = api.openGig(poster); // the same dates
			TestAccount talent = api.talent("D" + round, "photographer");
			UUID appA = api.applyOk(talent, gigA);
			UUID appB = api.applyOk(talent, gigB);
			api.shortlist(poster, appA, false).andExpect(status().isOk());
			api.shortlist(poster, appB, false).andExpect(status().isOk());
			UUID hireA = hires.start(poster.id(), appA).id();
			UUID hireB = hires.start(poster.id(), appB).id();

			List<Callable<String>> tasks = List.of(() -> run(() -> hires.confirm(talent.id(), hireA)),
					() -> run(() -> hires.confirm(talent.id(), hireB)));
			List<String> results = Concurrently.runAll(tasks);

			assertThat(results.stream().filter("ok"::equals)).hasSize(1);
			assertThat(results.stream().filter(HireBookingConflictException.class.getSimpleName()::equals)).hasSize(1);
			UUID winner = results.get(0).equals("ok") ? gigA : gigB;
			UUID loser = winner.equals(gigA) ? gigB : gigA;
			assertThat(gigStatus(winner)).isEqualTo("FILLED");
			assertThat(gigStatus(loser)).isEqualTo("OPEN"); // nothing of the refused confirmation remained
			assertThat(jdbc.sql("SELECT hire_pending FROM gigs.gig WHERE id = :id").param("id", loser).query(Boolean.class).single()).isTrue();
			assertThat(jdbc.sql("SELECT status FROM hiring.hire WHERE gig_id = :id").param("id", loser).query(String.class).single()).isEqualTo("PENDING");
			assertThat(count("SELECT count(*) FROM availability.booking WHERE gig_id = :p0", loser)).isZero();
			assertThat(count("SELECT count(*) FROM availability.booking WHERE account_id = :p0 AND status = 'ACTIVE'", talent.id())).isEqualTo(1);
		}
	}

	@Test
	void startingAHireAndWithdrawingTheApplicationAtOnceNeverLeavesAHireOnAWithdrawnApplication() throws Exception {
		int hired = 0;
		int withdrawn = 0;
		for (int round = 0; round < 12; round++) {
			TestAccount roundPoster = accounts.create(); // a Poster may make only 20 shortlist actions a day
			UUID gig = api.openGig(roundPoster);
			Shortlisted s = api.shortlisted(roundPoster, gig, "W" + round);

			List<Callable<String>> tasks = List.of(() -> run(() -> hires.start(roundPoster.id(), s.application())),
					() -> run(() -> withdrawing.withdraw(s.talent().id(), s.application())));
			List<String> results = Concurrently.runAll(tasks);

			String status = jdbc.sql("SELECT status FROM hiring.application WHERE id = :id").param("id", s.application()).query(String.class).single();
			int live = count("SELECT count(*) FROM hiring.hire WHERE application_id = :p0 AND status IN ('PENDING', 'CONFIRMED')", s.application());
			assertThat(status.equals("WITHDRAWN") && live > 0).as("a hire on a withdrawn application").isFalse();
			assertThat(results.stream().filter("ok"::equals)).as(results.toString()).hasSize(1); // exactly one of the two went through
			if (status.equals("ACTIVE")) {
				assertThat(results.get(1)).isEqualTo(HireInProgressException.class.getSimpleName());
				hired++;
			}
			else {
				assertThat(live).isZero();
				withdrawn++;
			}
		}
		assertThat(hired + withdrawn).isEqualTo(12);
	}

	@Test
	void startingAHireAndRemovingTheTalentFromTheShortlistAtOnceNeverLeavesAHireOnAnUnshortlistedTalent() throws Exception {
		for (int round = 0; round < 12; round++) {
			TestAccount roundPoster = accounts.create(); // a Poster may make only 20 shortlist actions a day
			UUID gig = api.openGig(roundPoster);
			Shortlisted s = api.shortlisted(roundPoster, gig, "S" + round);

			List<Callable<String>> tasks = List.of(() -> run(() -> hires.start(s.talent().id(), s.application())),
					() -> run(() -> shortlists.remove(roundPoster.id(), s.application())));
			List<String> results = Concurrently.runAll(tasks);

			boolean removed = jdbc.sql("SELECT removed_at IS NOT NULL FROM hiring.shortlist_entry WHERE application_id = :id").param("id", s.application())
					.query(Boolean.class).single();
			int live = count("SELECT count(*) FROM hiring.hire WHERE application_id = :p0 AND status IN ('PENDING', 'CONFIRMED')", s.application());
			assertThat(removed && live > 0).as("a hire for a Talent who is not shortlisted").isFalse();
			assertThat(results.stream().filter("ok"::equals)).as(results.toString()).hasSize(1);
		}
	}

	@Test
	void startingAHireWhileTheGigIsEditedNeverLetsTheExpiryPassTheGigsStart() throws Exception {
		int refusedEdits = 0;
		for (int round = 0; round < 8; round++) {
			UUID gig = api.openGig(poster); // starts in 3 days
			Shortlisted s = api.shortlisted(poster, gig, "E" + round);

			List<Callable<Integer>> tasks = List.of(() -> {
				hires.start(poster.id(), s.application());
				return 0;
			}, () -> api.gigs.edit(poster, gig, api.gigs.onSite().startsIn(Duration.ofHours(6)).lasting(Duration.ofHours(2)))
					.andReturn().getResponse().getStatus());
			int editStatus = Concurrently.runAll(tasks).get(1);

			StoredHire hire = hires.hire(poster.id(), jdbc.sql("SELECT id FROM hiring.hire WHERE gig_id = :g").param("g", gig).query(UUID.class).single());
			var start = jdbc.sql("SELECT start_at FROM gigs.gig WHERE id = :g").param("g", gig).query().singleRow().get("start_at");
			java.time.Instant gigStart = start instanceof java.time.OffsetDateTime o ? o.toInstant() : ((java.sql.Timestamp) start).toInstant();
			assertThat(hire.expiresAt()).isBeforeOrEqualTo(gigStart);
			assertThat(editStatus).isIn(200, 409);
			if (editStatus == 409) {
				refusedEdits++;
			}
		}
		assertThat(refusedEdits).isBetween(0, 8);
	}

}
