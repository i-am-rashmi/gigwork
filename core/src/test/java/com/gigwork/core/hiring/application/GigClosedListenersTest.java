package com.gigwork.core.hiring.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.gigs.GigCancelled;
import com.gigwork.core.gigs.application.GigExpiry;
import com.gigwork.core.hiring.HireCancelled;
import com.gigwork.core.hiring.HireEventCollector;
import com.gigwork.core.hiring.HireLapsed;
import com.gigwork.core.hiring.HireSide;
import com.gigwork.core.hiring.HireWithdrawn;
import com.gigwork.core.hiring.HiringApiSupport;
import com.gigwork.core.hiring.HiringApiSupport.Shortlisted;
import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/**
 * Hiring follows its gig being cancelled or expiring (FR-19, FR-27, FR-31). The listeners run after the gig's own
 * transaction, so each test waits for their effect; delivering an event twice changes nothing more.
 */
@IdentityIntegrationTest
@Import(HireEventCollector.class)
class GigClosedListenersTest {

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
	HireEventCollector collected;
	@Autowired
	GigExpiry gigExpiry;
	@Autowired
	TransactionTemplate transaction;
	@Autowired
	ApplicationEventPublisher publisher;

	HiringApiSupport api;
	TestAccount poster;
	UUID gig;
	Shortlisted shortlisted;
	UUID hire;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		collected.clear();
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		poster = accounts.create();
		gig = api.openGig(poster);
		shortlisted = api.shortlisted(poster, gig, "Asha");
		hire = HiringApiSupport.id(api.startHire(poster, shortlisted.application()).andExpect(status().isCreated()));
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	static void awaitTrue(String what, BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("timed out waiting for: " + what);
	}

	private String hireStatus(UUID id) {
		return jdbc.sql("SELECT status FROM hiring.hire WHERE id = :id").param("id", id).query(String.class).single();
	}

	private int activeBookings(UUID gigId) {
		return jdbc.sql("SELECT count(*) FROM availability.booking WHERE gig_id = :g AND status = 'ACTIVE'").param("g", gigId)
				.query(Integer.class).single();
	}

	@Test
	void cancellingAFilledGigCancelsTheHireWithTheGigsReasonAndReleasesTheBooking() throws Exception {
		api.confirmHire(shortlisted.talent(), hire).andExpect(status().isOk());
		assertThat(activeBookings(gig)).isEqualTo(1);

		api.gigs.cancel(poster, gig, "{\"reason\":\"Venue closed\"}").andExpect(status().isOk());

		awaitTrue("the hire to be cancelled", () -> hireStatus(hire).equals("CANCELLED"));
		awaitTrue("the booking to be released", () -> activeBookings(gig) == 0);
		var row = jdbc.sql("SELECT cancelled_by, cancel_reason, ended_at FROM hiring.hire WHERE id = :id").param("id", hire).query().singleRow();
		assertThat(row.get("cancelled_by")).isEqualTo("POSTER");
		assertThat(row.get("cancel_reason")).isEqualTo("Venue closed");
		assertThat(row.get("ended_at")).isNotNull();
		awaitTrue("the event", () -> collected.of(HireCancelled.class).size() == 1);
		assertThat(collected.of(HireCancelled.class).get(0).cancelledBy()).isEqualTo(HireSide.POSTER);
	}

	@Test
	void cancellingAGigWithAPendingHireWithdrawsTheHire() throws Exception {
		api.gigs.cancel(poster, gig, "{}").andExpect(status().isOk());

		awaitTrue("the hire to be withdrawn", () -> hireStatus(hire).equals("WITHDRAWN"));
		assertThat(collected.of(HireWithdrawn.class)).hasSize(1);
		assertThat(jdbc.sql("SELECT hire_pending FROM gigs.gig WHERE id = :id").param("id", gig).query(Boolean.class).single()).isFalse();
	}

	@Test
	void aGigThatExpiresWithAPendingHireLapsesTheHire() throws Exception {
		clock.advance(Duration.ofDays(4)); // the gig started; no one confirmed

		gigExpiry.run();

		awaitTrue("the hire to lapse", () -> hireStatus(hire).equals("LAPSED"));
		assertThat(collected.of(HireLapsed.class)).hasSize(1);
		assertThat(jdbc.sql("SELECT status FROM gigs.gig WHERE id = :id").param("id", gig).query(String.class).single()).isEqualTo("EXPIRED");
	}

	@Test
	void deliveringTheSameEventTwiceChangesNothingTheSecondTime() throws Exception {
		api.confirmHire(shortlisted.talent(), hire).andExpect(status().isOk());
		api.gigs.cancel(poster, gig, "{\"reason\":\"Venue closed\"}").andExpect(status().isOk());
		awaitTrue("the hire to be cancelled", () -> hireStatus(hire).equals("CANCELLED"));
		awaitTrue("the first event", () -> collected.of(HireCancelled.class).size() == 1);
		var before = jdbc.sql("SELECT ended_at, cancel_reason FROM hiring.hire WHERE id = :id").param("id", hire).query().singleRow();

		for (int i = 0; i < 2; i++) { // delivered again, twice
			transaction.executeWithoutResult(s -> publisher.publishEvent(new GigCancelled(gig, poster.id(), Instant.now(), true, "Venue closed")));
		}
		Thread.sleep(1500);

		assertThat(hireStatus(hire)).isEqualTo("CANCELLED");
		assertThat(jdbc.sql("SELECT ended_at, cancel_reason FROM hiring.hire WHERE id = :id").param("id", hire).query().singleRow()).isEqualTo(before);
		assertThat(collected.of(HireCancelled.class)).hasSize(1); // no second notification
		assertThat(activeBookings(gig)).isZero();
	}

	@Test
	void confirmingAndCancellingTheGigAtOnceNeverLeavesAFilledGigWithoutABooking() throws Exception {
		for (int round = 0; round < 6; round++) {
			UUID g = api.openGig(poster);
			Shortlisted s = api.shortlisted(poster, g, "C" + round);
			UUID h = HiringApiSupport.id(api.startHire(poster, s.application()).andExpect(status().isCreated()));

			List<Callable<Integer>> tasks = List.of(
					() -> api.confirmHire(s.talent(), h).andReturn().getResponse().getStatus(),
					() -> api.gigs.cancel(poster, g, "{\"reason\":\"Changed my mind\"}").andReturn().getResponse().getStatus());
			List<Integer> results = Concurrently.runAll(tasks);

			assertThat(results.get(1)).isEqualTo(200); // the cancel always succeeds: OPEN or FILLED
			assertThat(results.get(0)).isIn(200, 409);
			awaitTrue("the hire to end", () -> !List.of("PENDING", "CONFIRMED").contains(hireStatus(h)));
			awaitTrue("the booking to be gone", () -> activeBookings(g) == 0);
			assertThat(jdbc.sql("SELECT status FROM gigs.gig WHERE id = :id").param("id", g).query(String.class).single()).isEqualTo("CANCELLED");
			// confirm first: the hire was cancelled (booking released by the listener); cancel first: the hire was withdrawn
			assertThat(hireStatus(h)).isEqualTo(results.get(0) == 200 ? "CANCELLED" : "WITHDRAWN");
		}
	}

}
