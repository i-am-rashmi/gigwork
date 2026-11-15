package com.gigwork.core.gigs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/**
 * What Hiring may do to a gig (FR-27, FR-27a): hold, release, fill, reopen or expire. Each call is one conditional
 * statement, and the hold blocks editing; cancelling a filled gig is allowed (FR-19).
 */
@IdentityIntegrationTest
@RecordApplicationEvents
class GigHiringTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	ReferenceData reference;
	@Autowired
	MutableClock clock;
	@Autowired
	TestAccounts accounts;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	GigHiring hiring;
	@Autowired
	TransactionTemplate transaction;
	@Autowired
	ApplicationEvents events;

	GigApiSupport api;
	TestAccount poster;
	UUID gig;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		api = new GigApiSupport(mvc, reference, clock);
		poster = accounts.create();
		gig = api.publishOk(poster, api.onSite());
		events.clear();
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	private <T> T inTransaction(java.util.function.Supplier<T> work) {
		return transaction.execute(status -> work.get());
	}

	private String statusOf(UUID id) {
		return jdbc.sql("SELECT status FROM gigs.gig WHERE id = :id").param("id", id).query(String.class).single();
	}

	private boolean pending(UUID id) {
		return jdbc.sql("SELECT hire_pending FROM gigs.gig WHERE id = :id").param("id", id).query(Boolean.class).single();
	}

	// ---- the hold -------------------------------------------------------------------------------------------------------

	@Test
	void anOpenGigThatHasNotStartedCanBeHeldAndReleased() {
		assertThat(inTransaction(() -> hiring.holdForHire(gig, clock.instant()))).isTrue();
		assertThat(pending(gig)).isTrue();
		assertThat(statusOf(gig)).isEqualTo("OPEN");

		assertThat(inTransaction(() -> hiring.releaseHold(gig))).isTrue();
		assertThat(pending(gig)).isFalse();
	}

	@Test
	void aGigThatStartedOrIsNotOpenCannotBeHeld() {
		clock.advance(Duration.ofDays(4)); // started three days from the start of the test
		assertThat(inTransaction(() -> hiring.holdForHire(gig, clock.instant()))).isFalse();
		clock.reset();

		jdbc.sql("UPDATE gigs.gig SET status = 'CANCELLED', closed_at = now() WHERE id = :id").param("id", gig).update();
		assertThat(inTransaction(() -> hiring.holdForHire(gig, clock.instant()))).isFalse();
		assertThat(inTransaction(() -> hiring.releaseHold(gig))).isFalse();
		assertThat(inTransaction(() -> hiring.holdForHire(UUID.randomUUID(), clock.instant()))).isFalse();
	}

	@Test
	void aHeldGigCannotBeEditedAndCanBeAgainOnceReleased() throws Exception {
		inTransaction(() -> hiring.holdForHire(gig, clock.instant()));

		api.edit(poster, gig, api.onSite().title("Changed")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Hire pending"));
		assertThat(jdbc.sql("SELECT title FROM gigs.gig WHERE id = :id").param("id", gig).query(String.class).single())
				.isNotEqualTo("Changed");

		inTransaction(() -> hiring.releaseHold(gig));
		api.edit(poster, gig, api.onSite().title("Changed")).andExpect(status().isOk());
	}

	@Test
	void theHoldOnlyExistsOnAnOpenGig() {
		inTransaction(() -> hiring.holdForHire(gig, clock.instant()));
		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> jdbc.sql("UPDATE gigs.gig SET status = 'FILLED' WHERE id = :id").param("id", gig).update())
				.hasMessageContaining("gig_hire_pending_only_when_open");
	}

	@Test
	void expiryClearsTheHold() {
		inTransaction(() -> hiring.holdForHire(gig, clock.instant()));
		clock.advance(Duration.ofDays(4));

		jdbc.sql("UPDATE gigs.gig SET status = 'EXPIRED', closed_at = now(), hire_pending = false WHERE id = :id").param("id", gig).update();

		assertThat(pending(gig)).isFalse();
	}

	// ---- filling and reopening ------------------------------------------------------------------------------------------

	@Test
	void fillingMakesTheGigFilledAndEndsTheHoldExactlyOnce() {
		inTransaction(() -> hiring.holdForHire(gig, clock.instant()));

		assertThat(inTransaction(() -> hiring.fill(gig, clock.instant()))).isTrue();
		assertThat(statusOf(gig)).isEqualTo("FILLED");
		assertThat(pending(gig)).isFalse();
		assertThat(inTransaction(() -> hiring.fill(gig, clock.instant()))).isFalse();
		assertThat(inTransaction(() -> hiring.holdForHire(gig, clock.instant()))).isFalse();
	}

	@Test
	void aGigThatStartedCannotBeFilled() {
		clock.advance(Duration.ofDays(4));
		assertThat(inTransaction(() -> hiring.fill(gig, clock.instant()))).isFalse();
		assertThat(statusOf(gig)).isEqualTo("OPEN");
	}

	@Test
	void aFilledGigReopensBeforeItsStartAndExpiresAfter() {
		inTransaction(() -> hiring.fill(gig, clock.instant()));

		assertThat(inTransaction(() -> hiring.reopenOrExpire(gig, clock.instant()))).isEqualTo(GigHiring.Outcome.REOPENED);
		assertThat(statusOf(gig)).isEqualTo("OPEN");
		assertThat(jdbc.sql("SELECT closed_at IS NULL FROM gigs.gig WHERE id = :id").param("id", gig).query(Boolean.class).single()).isTrue();
		assertThat(events.stream(GigExpired.class).count()).isZero();

		inTransaction(() -> hiring.fill(gig, clock.instant()));
		clock.advance(Duration.ofDays(4)); // the gig has started
		assertThat(inTransaction(() -> hiring.reopenOrExpire(gig, clock.instant()))).isEqualTo(GigHiring.Outcome.EXPIRED);
		assertThat(statusOf(gig)).isEqualTo("EXPIRED");
		assertThat(jdbc.sql("SELECT closed_at IS NOT NULL FROM gigs.gig WHERE id = :id").param("id", gig).query(Boolean.class).single()).isTrue();
		assertThat(events.stream(GigExpired.class).toList()).singleElement().satisfies(e -> {
			assertThat(e.gigId()).isEqualTo(gig);
			assertThat(e.posterId()).isEqualTo(poster.id());
		});
	}

	@Test
	void onlyAFilledGigReopens() {
		assertThat(inTransaction(() -> hiring.reopenOrExpire(gig, clock.instant()))).isEqualTo(GigHiring.Outcome.NOT_FILLED);
		assertThat(statusOf(gig)).isEqualTo("OPEN");
	}

	// ---- cancelling a filled gig ----------------------------------------------------------------------------------------

	@Test
	void aFilledGigCanBeCancelledAndTheEventSaysSo() throws Exception {
		inTransaction(() -> hiring.fill(gig, clock.instant()));
		events.clear();

		api.cancel(poster, gig, "{\"reason\":\"Venue closed\"}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

		assertThat(statusOf(gig)).isEqualTo("CANCELLED");
		assertThat(events.stream(GigCancelled.class).toList()).singleElement().satisfies(e -> {
			assertThat(e.wasFilled()).isTrue();
			assertThat(e.reason()).isEqualTo("Venue closed");
		});
	}

	@Test
	void cancellingAnOpenGigSaysItWasNotFilledAndClearsTheHold() throws Exception {
		inTransaction(() -> hiring.holdForHire(gig, clock.instant()));
		events.clear();

		api.cancel(poster, gig, "{}").andExpect(status().isOk());

		assertThat(events.stream(GigCancelled.class).toList()).singleElement().satisfies(e -> {
			assertThat(e.wasFilled()).isFalse();
			assertThat(e.reason()).isNull();
		});
		assertThat(pending(gig)).isFalse();
	}

}
