package com.gigwork.core.gigs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.gigs.GigApiSupport.Gig;
import com.gigwork.core.gigs.application.GigExpiry;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

import io.micrometer.core.instrument.MeterRegistry;

/** The expiry job's work (FR-31, ADR-0010): open gigs whose start has passed become EXPIRED, once, with one event. */
@IdentityIntegrationTest
@Import(EventCollector.class)
class GigExpiryTest {

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
	GigExpiry expiry;
	@Autowired
	EventCollector collected;
	@Autowired
	MeterRegistry meters;

	GigApiSupport api;
	TestAccount poster;

	@BeforeEach
	void setUp() {
		clock.reset();
		jdbc.sql("DELETE FROM gigs.gig").update();
		api = new GigApiSupport(mvc, reference, clock);
		poster = accounts.create();
		collected.clear();
	}

	@AfterEach
	void tearDown() {
		clock.reset();
		jdbc.sql("DELETE FROM gigs.gig").update();
	}

	private UUID publish(Duration startsIn) throws Exception {
		return api.publishOk(poster, api.onSite().startsIn(startsIn));
	}

	private Map<String, Object> row(UUID id) {
		return jdbc.sql("SELECT * FROM gigs.gig WHERE id = :id").param("id", id).query().singleRow();
	}

	private double rowsMetric() {
		var counter = meters.find("gigwork.job.rows").tag("job", "gig-expiry").counter();
		return counter == null ? 0 : counter.count();
	}

	@Test
	void anOpenGigWhoseStartHasPassedExpiresAndOthersAreLeftAlone() throws Exception {
		UUID due = publish(Duration.ofHours(1));
		UUID later = publish(Duration.ofHours(5));
		UUID cancelled = publish(Duration.ofHours(1));
		api.cancel(poster, cancelled, "{}").andExpect(status().isOk());
		UUID filled = publish(Duration.ofHours(1));
		jdbc.sql("UPDATE gigs.gig SET status = 'FILLED' WHERE id = :id").param("id", filled).update();
		collected.clear();

		clock.advance(Duration.ofHours(2));
		long changed = expiry.run();

		assertThat(changed).isEqualTo(1);
		Map<String, Object> expired = row(due);
		assertThat(expired.get("status")).isEqualTo("EXPIRED");
		assertThat(expired.get("closed_at")).isNotNull();
		assertThat(expired.get("close_reason")).isNull();
		assertThat(row(later).get("status")).isEqualTo("OPEN");
		assertThat(row(cancelled).get("status")).isEqualTo("CANCELLED");
		assertThat(row(filled).get("status")).isEqualTo("FILLED");
		assertThat(collected.of(GigExpired.class)).hasSize(1);
		assertThat(collected.of(GigExpired.class).get(0).gigId()).isEqualTo(due);
		assertThat(collected.of(GigExpired.class).get(0).posterId()).isEqualTo(poster.id());
		assertThat(collected.of(GigExpired.class).get(0).expiredAt()).isEqualTo(clock.instant());
	}

	@Test
	void aGigExpiresExactlyAtItsStartNotBefore() throws Exception {
		UUID gig = publish(Duration.ofHours(1));
		Instant start = jdbc.sql("SELECT start_at FROM gigs.gig WHERE id = :id").param("id", gig).query(java.time.OffsetDateTime.class)
				.single().toInstant();

		clock.set(start.minus(Duration.ofNanos(1_000)));
		assertThat(expiry.run()).isZero();
		assertThat(row(gig).get("status")).isEqualTo("OPEN");

		clock.set(start);
		assertThat(expiry.run()).isEqualTo(1);
		assertThat(row(gig).get("status")).isEqualTo("EXPIRED");
	}

	@Test
	void aSecondRunChangesNothingAndWritesNoSecondEvent() throws Exception {
		publish(Duration.ofHours(1));
		publish(Duration.ofHours(1));
		clock.advance(Duration.ofHours(2));
		collected.clear();

		assertThat(expiry.run()).isEqualTo(2);
		assertThat(expiry.run()).isZero();

		assertThat(collected.of(GigExpired.class)).hasSize(2);
	}

	@Test
	void aRunRecordsTheRowsItChanged() throws Exception {
		publish(Duration.ofHours(1));
		publish(Duration.ofHours(1));
		publish(Duration.ofHours(1));
		clock.advance(Duration.ofHours(2));
		double before = rowsMetric();

		expiry.run();
		expiry.run();

		assertThat(rowsMetric() - before).isEqualTo(3);
		assertThat(meters.find("gigwork.job.duration").tag("job", "gig-expiry").tag("outcome", "success").timer().count())
				.isGreaterThanOrEqualTo(2);
	}

	@Test
	void anExpiredGigLeavesTheListAndTheDirectViewButStaysInThePostersOwnList() throws Exception {
		UUID gig = publish(Duration.ofHours(1));
		clock.advance(Duration.ofHours(2));
		expiry.run();
		poster = new TestAccount(poster.id(), accounts.token(poster.id())); // tokens expire with the clock
		TestAccount other = accounts.create();

		api.get(other, "/gigs").andExpect(jsonPath("$.items.length()").value(0));
		api.get(other, "/gigs/" + gig).andExpect(status().isNotFound());
		api.get(poster, "/me/gigs").andExpect(jsonPath("$.items[0].status").value("EXPIRED")).andExpect(jsonPath("$.items[0].closedAt").isNotEmpty());
		api.edit(poster, gig, api.onSite().startsIn(Duration.ofDays(2))).andExpect(status().isConflict());
		api.cancel(poster, gig, "{}").andExpect(status().isConflict());
	}

	@Test
	void aGigThatHasStartedCannotBeEditedEvenBeforeTheSweepHasRun() throws Exception {
		UUID gig = publish(Duration.ofHours(1));
		clock.advance(Duration.ofHours(2)); // the job has not run
		poster = new TestAccount(poster.id(), accounts.token(poster.id()));

		assertThat(row(gig).get("status")).isEqualTo("OPEN");
		Gig attempt = api.onSite().startsIn(Duration.ofDays(2));
		api.edit(poster, gig, attempt).andExpect(status().isConflict());
		assertThat(row(gig).get("status")).isEqualTo("OPEN");
		assertThat(List.of(row(gig).get("title"))).containsExactly("Photographer for a two-day wedding");
	}

}
