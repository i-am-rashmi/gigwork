package com.gigwork.core.availability.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.gigwork.core.availability.ConflictingPeriod;

/**
 * Bookings. Every write is one native statement and the database decides (ADR-0002, ADR-0008): the exclusion
 * constraint {@code booking_no_overlap} refuses an overlapping active booking and the partial unique index
 * {@code booking_one_active_per_gig} a second active booking for a gig.
 */
@Repository
public class BookingStore {

	/** What an INSERT of a booking can be refused with. */
	public static final String OVERLAP_STATE = "23P01";
	public static final String UNIQUE_STATE = "23505";
	public static final String ONE_ACTIVE_PER_GIG = "booking_one_active_per_gig";

	private final JdbcClient jdbc;

	BookingStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Makes this Talent's bookings take turns until the transaction ends. The exclusion constraint alone is enough
	 * for correctness, but two simultaneous overlapping INSERTs each wait for the other's uncommitted row, and
	 * PostgreSQL resolves that by aborting one of them with a (slow) deadlock error. Taking turns avoids it; the
	 * constraint still decides every booking.
	 */
	@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
	public void lockTalent(UUID accountId) {
		jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", "booking:" + accountId).query().singleRow();
	}

	/** One INSERT; throws a DataIntegrityViolationException (SQLState 23P01 or 23505) if the database refuses it. */
	public void insert(UUID id, UUID accountId, UUID gigId, Instant start, Instant end, Instant now) {
		jdbc.sql("""
				INSERT INTO availability.booking (id, account_id, gig_id, period, status, created_at)
				VALUES (:id, :a, :g, tstzrange(CAST(:start AS timestamptz), CAST(:end AS timestamptz), '[)'), 'ACTIVE', :now)""")
				.param("id", id).param("a", accountId).param("g", gigId).param("start", BusyBlockStore.ts(start))
				.param("end", BusyBlockStore.ts(end)).param("now", BusyBlockStore.ts(now)).update();
	}

	/** The active booking of this Talent for this gig with exactly this period, if there is one. */
	public Optional<UUID> findActive(UUID accountId, UUID gigId, Instant start, Instant end) {
		return jdbc.sql("""
				SELECT id FROM availability.booking
				WHERE account_id = :a AND gig_id = :g AND status = 'ACTIVE'
				  AND period = tstzrange(CAST(:start AS timestamptz), CAST(:end AS timestamptz), '[)')""")
				.param("a", accountId).param("g", gigId).param("start", BusyBlockStore.ts(start))
				.param("end", BusyBlockStore.ts(end)).query(UUID.class).optional();
	}

	/** One conditional UPDATE; true if it cancelled an active booking. */
	public boolean cancelActive(UUID gigId, Instant now) {
		return jdbc.sql("""
				UPDATE availability.booking SET status = 'CANCELLED', cancelled_at = :now
				WHERE gig_id = :g AND status = 'ACTIVE'""").param("g", gigId).param("now", BusyBlockStore.ts(now)).update() > 0;
	}

	/** Periods of the Talent's busy blocks and active bookings overlapping {@code [start, end)}, with no IDs. */
	public List<ConflictingPeriod> overlappingPeriods(UUID accountId, Instant start, Instant end) {
		return jdbc.sql("""
				SELECT lower(period) AS start, upper(period) AS stop, 'BUSY_BLOCK' AS kind FROM availability.busy_block
				WHERE account_id = :a AND period && tstzrange(CAST(:start AS timestamptz), CAST(:end AS timestamptz), '[)')
				UNION ALL
				SELECT lower(period), upper(period), 'BOOKING' FROM availability.booking
				WHERE account_id = :a AND status = 'ACTIVE'
				  AND period && tstzrange(CAST(:start AS timestamptz), CAST(:end AS timestamptz), '[)')
				ORDER BY 1, 2, 3""")
				.param("a", accountId).param("start", BusyBlockStore.ts(start)).param("end", BusyBlockStore.ts(end))
				.query(BookingStore::period).list();
	}

	/** The account's active bookings overlapping {@code [from, to)}, for the calendar. */
	public List<CalendarEntry> activeOverlapping(UUID accountId, Instant from, Instant to) {
		return jdbc.sql("""
				SELECT id, gig_id, lower(period) AS start, upper(period) AS stop FROM availability.booking
				WHERE account_id = :a AND status = 'ACTIVE'
				  AND period && tstzrange(CAST(:from AS timestamptz), CAST(:to AS timestamptz), '[)')""")
				.param("a", accountId).param("from", BusyBlockStore.ts(from)).param("to", BusyBlockStore.ts(to))
				.query((rs, n) -> new CalendarEntry(CalendarEntry.Type.BOOKING, rs.getObject("id", UUID.class),
						rs.getObject("start", OffsetDateTime.class).toInstant(), rs.getObject("stop", OffsetDateTime.class).toInstant(),
						false, null, null, rs.getObject("gig_id", UUID.class)))
				.list();
	}

	private static ConflictingPeriod period(ResultSet rs, int row) throws SQLException {
		return new ConflictingPeriod(rs.getObject("start", OffsetDateTime.class).toInstant(),
				rs.getObject("stop", OffsetDateTime.class).toInstant(), ConflictingPeriod.Kind.valueOf(rs.getString("kind")));
	}

}
