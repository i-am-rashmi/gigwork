package com.gigwork.core.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.gigwork.core.reference.support.ReferenceIntegrationTest;

/**
 * The guarantees of the availability schema live in the database: each test breaks a rule with SQL. Not
 * transactional, because PostgreSQL aborts a transaction after the first failed statement; each test uses its own
 * accounts and removes the rows afterwards.
 */
@ReferenceIntegrationTest
class AvailabilitySchemaTest {

	@Autowired
	JdbcClient jdbc;

	private final UUID account = UUID.randomUUID();

	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM availability.busy_block WHERE account_id = :a").param("a", account).update();
		jdbc.sql("DELETE FROM availability.booking WHERE account_id = :a").param("a", account).update();
	}

	private int block(String period, boolean allDay, String firstDate, String lastDate) {
		return jdbc.sql("INSERT INTO availability.busy_block (id, account_id, period, all_day, start_date, end_date, created_at) VALUES "
				+ "(gen_random_uuid(), :a, '" + period + "'::tstzrange, " + allDay + ", " + date(firstDate) + ", " + date(lastDate) + ", now())")
				.param("a", account).update();
	}

	private int booking(UUID gig, String period, String status) {
		return jdbc.sql("INSERT INTO availability.booking (id, account_id, gig_id, period, status, created_at, cancelled_at) VALUES "
				+ "(gen_random_uuid(), :a, :g, '" + period + "'::tstzrange, '" + status + "', now(), "
				+ ("CANCELLED".equals(status) ? "now()" : "NULL") + ")").param("a", account).param("g", gig).update();
	}

	private static String date(String d) {
		return d == null ? "NULL" : "DATE '" + d + "'";
	}

	private static final String TEN_TO_TWO = "[2030-01-01 10:00+00,2030-01-01 14:00+00)";
	private static final String TWO_TO_SIX = "[2030-01-01 14:00+00,2030-01-01 18:00+00)";
	private static final String NOON_TO_ONE = "[2030-01-01 12:00+00,2030-01-01 13:00+00)";

	// ---- period checks (both tables) --------------------------------------------------------------------------------

	@Test
	void aPeriodIsHalfOpenFiniteAndNotEmpty() {
		for (String bad : new String[] { "empty", "[2030-01-01 10:00+00,2030-01-01 10:00+00)", "(2030-01-01 10:00+00,2030-01-01 14:00+00)",
				"[2030-01-01 10:00+00,2030-01-01 14:00+00]", "[2030-01-01 10:00+00,)", "(,2030-01-01 14:00+00)", "[2030-01-01 10:00+00,infinity)" }) {
			assertThatThrownBy(() -> block(bad, false, null, null)).as("busy block " + bad).isInstanceOf(DataIntegrityViolationException.class);
			assertThatThrownBy(() -> booking(UUID.randomUUID(), bad, "ACTIVE")).as("booking " + bad).isInstanceOf(DataIntegrityViolationException.class);
		}
		block(TEN_TO_TWO, false, null, null);
		booking(UUID.randomUUID(), TEN_TO_TWO, "ACTIVE");
	}

	// ---- busy blocks ------------------------------------------------------------------------------------------------------

	@Test
	void busyBlocksMayOverlapEachOtherAndEvenRepeat() {
		block(TEN_TO_TWO, false, null, null);
		block(NOON_TO_ONE, false, null, null);
		block(TEN_TO_TWO, false, null, null);

		assertThat(jdbc.sql("SELECT count(*) FROM availability.busy_block WHERE account_id = :a").param("a", account).query(Integer.class).single()).isEqualTo(3);
	}

	@Test
	void anAllDayBlockHasBothDatesAndOtherBlocksHaveNone() {
		block(TEN_TO_TWO, true, "2030-01-01", "2030-01-02");
		block(TEN_TO_TWO, true, "2030-01-01", "2030-01-01");
		block(TEN_TO_TWO, false, null, null);

		assertThatThrownBy(() -> block(TEN_TO_TWO, true, null, null)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> block(TEN_TO_TWO, true, "2030-01-01", null)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> block(TEN_TO_TWO, true, "2030-01-02", "2030-01-01")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> block(TEN_TO_TWO, false, "2030-01-01", "2030-01-01")).isInstanceOf(DataIntegrityViolationException.class);
	}

	// ---- bookings ---------------------------------------------------------------------------------------------------------

	@Test
	void activeBookingsOfOneTalentCannotOverlap() {
		booking(UUID.randomUUID(), TEN_TO_TWO, "ACTIVE");

		assertThatThrownBy(() -> booking(UUID.randomUUID(), NOON_TO_ONE, "ACTIVE")).isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("booking_no_overlap");
		assertThatThrownBy(() -> booking(UUID.randomUUID(), TEN_TO_TWO, "ACTIVE")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void backToBackBookingsAreFine() {
		booking(UUID.randomUUID(), TEN_TO_TWO, "ACTIVE");

		booking(UUID.randomUUID(), TWO_TO_SIX, "ACTIVE"); // [10,14) and [14,18) share no instant
	}

	@Test
	void cancelledBookingsOverlapFreely() {
		booking(UUID.randomUUID(), TEN_TO_TWO, "CANCELLED");
		booking(UUID.randomUUID(), TEN_TO_TWO, "CANCELLED");
		booking(UUID.randomUUID(), NOON_TO_ONE, "ACTIVE");
	}

	@Test
	void differentTalentCanBeBookedForTheSameTime() {
		booking(UUID.randomUUID(), TEN_TO_TWO, "ACTIVE");
		UUID other = UUID.randomUUID();
		try {
			jdbc.sql("INSERT INTO availability.booking (id, account_id, gig_id, period, created_at) VALUES (gen_random_uuid(), :a, gen_random_uuid(), '"
					+ TEN_TO_TWO + "'::tstzrange, now())").param("a", other).update();
			assertThat(jdbc.sql("SELECT count(*) FROM availability.booking WHERE account_id = :a").param("a", other).query(Integer.class).single()).isEqualTo(1);
		}
		finally {
			jdbc.sql("DELETE FROM availability.booking WHERE account_id = :a").param("a", other).update();
		}
	}

	@Test
	void aGigHasAtMostOneActiveBooking() {
		UUID gig = UUID.randomUUID();
		booking(gig, TEN_TO_TWO, "ACTIVE");

		assertThatThrownBy(() -> booking(gig, "[2030-02-01 10:00+00,2030-02-01 14:00+00)", "ACTIVE")).isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("booking_one_active_per_gig");
		booking(gig, "[2030-02-01 10:00+00,2030-02-01 14:00+00)", "CANCELLED");
		booking(gig, "[2030-03-01 10:00+00,2030-03-01 14:00+00)", "CANCELLED");
	}

	@Test
	void theStatusIsOneOfTwoAndCancelledAtMatchesIt() {
		assertThatThrownBy(() -> booking(UUID.randomUUID(), TEN_TO_TWO, "DONE")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.sql("INSERT INTO availability.booking (id, account_id, gig_id, period, status, created_at, cancelled_at) VALUES "
				+ "(gen_random_uuid(), :a, gen_random_uuid(), '" + TEN_TO_TWO + "'::tstzrange, 'ACTIVE', now(), now())").param("a", account).update())
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.sql("INSERT INTO availability.booking (id, account_id, gig_id, period, status, created_at) VALUES "
				+ "(gen_random_uuid(), :a, gen_random_uuid(), '" + TEN_TO_TWO + "'::tstzrange, 'CANCELLED', now())").param("a", account).update())
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
