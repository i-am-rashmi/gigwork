package com.gigwork.core.availability.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busy blocks. Native SQL through JdbcClient: PostgreSQL range columns have no JPA mapping (ADR-0008). The limit
 * on upcoming blocks is an invariant, so {@link #lockTalent} must be called, in the same transaction, before
 * {@link #countUpcoming} and {@link #insert}: it serialises the adds of one Talent.
 */
@Repository
public class BusyBlockStore {

	private final JdbcClient jdbc;

	BusyBlockStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	static OffsetDateTime ts(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	/** Takes a per-Talent transaction-scoped advisory lock: concurrent adds of one Talent wait for each other. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockTalent(UUID accountId) {
		jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", "busy_block:" + accountId).query().singleRow();
	}

	/** Blocks that have not ended yet. */
	@Transactional(propagation = Propagation.MANDATORY)
	public int countUpcoming(UUID accountId, Instant now) {
		return jdbc.sql("SELECT count(*) FROM availability.busy_block WHERE account_id = :a AND upper(period) > :now")
				.param("a", accountId).param("now", ts(now)).query(Integer.class).single();
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void insert(UUID id, UUID accountId, Instant start, Instant end, boolean allDay, LocalDate firstDate,
			LocalDate lastDate, Instant now) {
		jdbc.sql("""
				INSERT INTO availability.busy_block (id, account_id, period, all_day, start_date, end_date, created_at)
				VALUES (:id, :a, tstzrange(CAST(:start AS timestamptz), CAST(:end AS timestamptz), '[)'), :allDay,
				        :first, :last, :now)""")
				.param("id", id).param("a", accountId).param("start", ts(start)).param("end", ts(end))
				.param("allDay", allDay).param("first", firstDate).param("last", lastDate).param("now", ts(now)).update();
	}

	/** Deletes the block only if it belongs to the account; false for someone else's or an unknown block. */
	@Transactional
	public boolean delete(UUID id, UUID accountId) {
		return jdbc.sql("DELETE FROM availability.busy_block WHERE id = :id AND account_id = :a").param("id", id)
				.param("a", accountId).update() == 1;
	}

	public CalendarEntry find(UUID id, UUID accountId) {
		return jdbc.sql(SELECT + " WHERE id = :id AND account_id = :a").param("id", id).param("a", accountId)
				.query(BusyBlockStore::map).optional().orElse(null);
	}

	/** The account's blocks overlapping {@code [from, to)}. */
	public List<CalendarEntry> overlapping(UUID accountId, Instant from, Instant to) {
		return jdbc.sql(SELECT + " WHERE account_id = :a AND period && tstzrange(CAST(:from AS timestamptz), CAST(:to AS timestamptz), '[)')")
				.param("a", accountId).param("from", ts(from)).param("to", ts(to)).query(BusyBlockStore::map).list();
	}

	private static final String SELECT = """
			SELECT id, lower(period) AS start, upper(period) AS stop, all_day, start_date, end_date
			FROM availability.busy_block""";

	private static CalendarEntry map(ResultSet rs, int row) throws SQLException {
		return new CalendarEntry(CalendarEntry.Type.BUSY_BLOCK, rs.getObject("id", UUID.class),
				rs.getObject("start", OffsetDateTime.class).toInstant(), rs.getObject("stop", OffsetDateTime.class).toInstant(),
				rs.getBoolean("all_day"), rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class), null);
	}

}
