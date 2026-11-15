package com.gigwork.core.hiring.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.hiring.HireEventCollector;
import com.gigwork.core.hiring.HireLapsed;
import com.gigwork.core.hiring.HiringApiSupport;
import com.gigwork.core.hiring.HiringApiSupport.Shortlisted;
import com.gigwork.core.hiring.persistence.HireStore;
import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/**
 * The lapse job (FR-27, ADR-0010): what it changes, that two runs at once change each hire once, and that confirming
 * a hire and lapsing it at the deadline give exactly one outcome.
 */
@IdentityIntegrationTest
@Import(HireEventCollector.class)
@org.springframework.test.context.TestPropertySource(properties = "gigwork.hiring.lapse-batch-size=5")
class HireLapseTest {

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
	HireLapse lapse;
	@Autowired
	HireStore store;
	@Autowired
	TransactionTemplate transaction;
	@Autowired
	HireEventCollector collected;

	HiringApiSupport api;
	TestAccount poster;

	@BeforeEach
	void setUp() {
		clock.reset();
		collected.clear();
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		poster = accounts.create();
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	private UUID pendingHire() throws Exception {
		UUID gig = api.openGig(poster);
		Shortlisted s = api.shortlisted(poster, gig, "T" + UUID.randomUUID().toString().substring(0, 6));
		return HiringApiSupport.id(api.startHire(poster, s.application()).andExpect(status().isCreated()));
	}

	private String statusOf(UUID hire) {
		return jdbc.sql("SELECT status FROM hiring.hire WHERE id = :id").param("id", hire).query(String.class).single();
	}

	@Test
	void aPendingHirePastItsExpiryLapsesAndItsGigIsFreedWhileOthersAreLeftAlone() throws Exception {
		UUID due = pendingHire();
		UUID gigOfDue = jdbc.sql("SELECT gig_id FROM hiring.hire WHERE id = :id").param("id", due).query(UUID.class).single();
		clock.advance(Duration.ofHours(25));
		TestAccount posterNow = new TestAccount(poster.id(), accounts.token(poster.id()));
		poster = posterNow;
		UUID notDue = pendingHire(); // started now: not due
		UUID confirmed = pendingHire();
		var confirmedHire = jdbc.sql("SELECT talent_id FROM hiring.hire WHERE id = :id").param("id", confirmed).query(UUID.class).single();
		api.confirmHire(new TestAccount(confirmedHire, accounts.token(confirmedHire)), confirmed).andExpect(status().isOk());

		assertThat(lapse.run()).isEqualTo(1);

		assertThat(statusOf(due)).isEqualTo("LAPSED");
		assertThat(jdbc.sql("SELECT ended_at IS NOT NULL FROM hiring.hire WHERE id = :id").param("id", due).query(Boolean.class).single()).isTrue();
		assertThat(jdbc.sql("SELECT hire_pending FROM gigs.gig WHERE id = :id").param("id", gigOfDue).query(Boolean.class).single()).isFalse();
		assertThat(statusOf(notDue)).isEqualTo("PENDING");
		assertThat(statusOf(confirmed)).isEqualTo("CONFIRMED");
		assertThat(collected.of(HireLapsed.class)).singleElement().satisfies(e -> assertThat(e.hireId()).isEqualTo(due));
		assertThat(lapse.run()).isZero(); // nothing more is due
	}

	@Test
	void aLapsedHireCannotBeConfirmedAndTheGigCanBeEditedAndHiredAgain() throws Exception {
		UUID hire = pendingHire();
		var row = jdbc.sql("SELECT gig_id, talent_id, application_id FROM hiring.hire WHERE id = :id").param("id", hire).query().singleRow();
		clock.advance(Duration.ofHours(25));
		lapse.run();
		TestAccount talent = new TestAccount((UUID) row.get("talent_id"), accounts.token((UUID) row.get("talent_id")));
		poster = new TestAccount(poster.id(), accounts.token(poster.id()));

		api.confirmHire(talent, hire).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_NOT_PENDING"));
		api.gigs.edit(poster, (UUID) row.get("gig_id"), api.gigs.onSite().startsIn(Duration.ofDays(5)).title("Edited")).andExpect(status().isOk());
		api.startHire(poster, (UUID) row.get("application_id")).andExpect(status().isCreated());
	}

	@Test
	void twoRunsAtOnceLapseEachHireOnceAndWriteOneEventEach() throws Exception {
		List<UUID> hires = new ArrayList<>();
		for (int i = 0; i < 12; i++) { // the batch is 5, so a run takes several transactions that interleave
			hires.add(pendingHire());
		}
		clock.advance(Duration.ofHours(25));
		collected.clear();
		List<Callable<Long>> tasks = List.of(lapse::run, lapse::run, lapse::run);

		List<Long> rows = Concurrently.runAll(tasks);

		assertThat(rows.stream().mapToLong(Long::longValue).sum()).isEqualTo(12);
		for (UUID hire : hires) {
			assertThat(statusOf(hire)).isEqualTo("LAPSED");
		}
		assertThat(collected.of(HireLapsed.class)).hasSize(12).extracting(HireLapsed::hireId).containsExactlyInAnyOrderElementsOf(hires);
	}

	@Test
	void confirmingAndLapsingAtTheDeadlineGiveExactlyOneOutcome() throws Exception {
		for (int round = 0; round < 6; round++) {
			UUID hire = pendingHire();
			Instant now = clock.instant();
			// two instances whose clocks differ by a moment either side of the deadline
			Instant expiry = jdbc.sql("SELECT expires_at FROM hiring.hire WHERE id = :id").param("id", hire).query(java.time.OffsetDateTime.class).single().toInstant();
			Instant confirmNow = expiry.minusMillis(1);
			Instant lapseNow = expiry.plusMillis(1);

			List<Callable<Boolean>> tasks = List.of(
					() -> Boolean.TRUE.equals(transaction.execute(s -> store.confirm(hire, confirmNow))),
					() -> Boolean.TRUE.equals(transaction.execute(s -> !store.lapseDue(lapseNow, 100).stream().filter(h -> h.id().equals(hire)).toList().isEmpty())));
			List<Boolean> results = Concurrently.runAll(tasks);

			assertThat(results.stream().filter(Boolean::booleanValue)).as("round %d at %s", round, now).hasSize(1);
			assertThat(statusOf(hire)).isEqualTo(results.get(0) ? "CONFIRMED" : "LAPSED");
		}
	}

}
