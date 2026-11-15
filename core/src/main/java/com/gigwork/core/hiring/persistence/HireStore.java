package com.gigwork.core.hiring.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gigwork.core.hiring.HireSide;

/**
 * Hires. Native SQL through JdbcClient (ADR-0008): every status change is one conditional statement, so two
 * simultaneous changes cannot both win (a confirmation against a lapse, two confirmations, a start against a start).
 * Row locks are always taken in the order application, hire, gig, booking.
 */
@Repository
public class HireStore {

	private static final String COLUMNS = """
			id, gig_id, application_id, talent_id, poster_id, started_by, status, started_at, expires_at, confirmed_at,
			ended_at, cancelled_by, cancel_reason, booking_id""";

	private final JdbcClient jdbc;

	HireStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	private static OffsetDateTime ts(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	/**
	 * Starts a hire: one INSERT; the partial unique index allows one PENDING hire per gig, so if there already is
	 * one this inserts nothing and returns false.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean insertPending(UUID id, UUID gigId, UUID applicationId, UUID talentId, UUID posterId, HireSide startedBy,
			Instant now, Instant expiresAt) {
		return jdbc.sql("""
				INSERT INTO hiring.hire (id, gig_id, application_id, talent_id, poster_id, started_by, status, started_at, expires_at)
				VALUES (:id, :gig, :application, :talent, :poster, :by, 'PENDING', :now, :expires)
				ON CONFLICT (gig_id) WHERE status = 'PENDING' DO NOTHING
				RETURNING id""")
				.param("id", id).param("gig", gigId).param("application", applicationId).param("talent", talentId)
				.param("poster", posterId).param("by", startedBy.name()).param("now", ts(now)).param("expires", ts(expiresAt))
				.query(UUID.class).optional().isPresent();
	}

	/** The gig was edited before it was held, so it may start earlier than assumed: the expiry never passes the start. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void capExpiry(UUID id, Instant gigStart) {
		jdbc.sql("UPDATE hiring.hire SET expires_at = LEAST(expires_at, :start) WHERE id = :id AND status = 'PENDING'")
				.param("id", id).param("start", ts(gigStart)).update();
	}

	/** PENDING to CONFIRMED, only if it has not expired. False if the hire is not pending or has expired. */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean confirm(UUID id, Instant now) {
		return jdbc.sql("""
				UPDATE hiring.hire SET status = 'CONFIRMED', confirmed_at = :now
				WHERE id = :id AND status = 'PENDING' AND expires_at > :now""").param("id", id).param("now", ts(now)).update() == 1;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void setBooking(UUID id, UUID bookingId) {
		jdbc.sql("UPDATE hiring.hire SET booking_id = :b WHERE id = :id").param("id", id).param("b", bookingId).update();
	}

	/** PENDING to DECLINED, WITHDRAWN or LAPSED; empty if the hire was not pending. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<StoredHire> endPending(UUID id, String toStatus, Instant now) {
		return jdbc.sql("UPDATE hiring.hire SET status = :to, ended_at = :now WHERE id = :id AND status = 'PENDING' RETURNING " + COLUMNS)
				.param("id", id).param("to", toStatus).param("now", ts(now)).query(HireStore::map).optional();
	}

	/** CONFIRMED to CANCELLED; empty if the hire was not confirmed. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<StoredHire> cancel(UUID id, HireSide by, String reason, Instant now) {
		return jdbc.sql("""
				UPDATE hiring.hire SET status = 'CANCELLED', ended_at = :now, cancelled_by = :by, cancel_reason = :reason
				WHERE id = :id AND status = 'CONFIRMED' RETURNING""" + " " + COLUMNS)
				.param("id", id).param("by", by.name()).param("reason", reason).param("now", ts(now)).query(HireStore::map).optional();
	}

	/** The gig's PENDING hire, ended as {@code toStatus} (WITHDRAWN or LAPSED); empty if it has none. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<StoredHire> endPendingOfGig(UUID gigId, String toStatus, Instant now) {
		return jdbc.sql("UPDATE hiring.hire SET status = :to, ended_at = :now WHERE gig_id = :gig AND status = 'PENDING' RETURNING " + COLUMNS)
				.param("gig", gigId).param("to", toStatus).param("now", ts(now)).query(HireStore::map).optional();
	}

	/** The gig's CONFIRMED hire, cancelled by the Poster because the gig was cancelled; empty if it has none. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<StoredHire> cancelConfirmedOfGig(UUID gigId, String reason, Instant now) {
		return jdbc.sql("""
				UPDATE hiring.hire SET status = 'CANCELLED', ended_at = :now, cancelled_by = 'POSTER', cancel_reason = :reason
				WHERE gig_id = :gig AND status = 'CONFIRMED' RETURNING""" + " " + COLUMNS)
				.param("gig", gigId).param("reason", reason).param("now", ts(now)).query(HireStore::map).optional();
	}

	/**
	 * The lapse sweep (ADR-0010): up to {@code limit} pending hires past their expiry become LAPSED, in one statement.
	 * The rows are locked with SKIP LOCKED, so two sweeps take different hires, and a hire being confirmed right now is
	 * left alone (it is then no longer pending).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<StoredHire> lapseDue(Instant now, int limit) {
		return jdbc.sql("""
				UPDATE hiring.hire SET status = 'LAPSED', ended_at = :now
				WHERE id IN (SELECT id FROM hiring.hire WHERE status = 'PENDING' AND expires_at <= :now
				             ORDER BY expires_at, id LIMIT :limit FOR UPDATE SKIP LOCKED)
				  AND status = 'PENDING'
				RETURNING""" + " " + COLUMNS).param("now", ts(now)).param("limit", limit).query(HireStore::map).list();
	}

	/** True if the application has a PENDING or CONFIRMED hire. Read after the application row is locked. */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean inProgress(UUID applicationId) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM hiring.hire WHERE application_id = :a AND status IN ('PENDING', 'CONFIRMED'))")
				.param("a", applicationId).query(Boolean.class).single();
	}

	public Optional<StoredHire> find(UUID id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM hiring.hire WHERE id = :id").param("id", id).query(HireStore::map).optional();
	}

	public Optional<StoredHire> findPendingOfGig(UUID gigId) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM hiring.hire WHERE gig_id = :g AND status = 'PENDING'").param("g", gigId)
				.query(HireStore::map).optional();
	}

	/**
	 * The application's CONFIRMED hire, locked FOR SHARE: a contact view reads it inside its own transaction, so a
	 * cancellation at the same moment waits until the view has been logged.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<StoredHire> lockConfirmedOfApplication(UUID applicationId) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM hiring.hire WHERE application_id = :a AND status = 'CONFIRMED' FOR SHARE")
				.param("a", applicationId).query(HireStore::map).optional();
	}

	/** The application's CONFIRMED hire, if it has one. */
	public Optional<StoredHire> findConfirmedOfApplication(UUID applicationId) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM hiring.hire WHERE application_id = :a AND status = 'CONFIRMED'")
				.param("a", applicationId).query(HireStore::map).optional();
	}

	/** The latest hire (any status) of each of the applications, in one query; applications never hired are absent. */
	public List<StoredHire> latestOf(java.util.Collection<UUID> applicationIds) {
		if (applicationIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("SELECT DISTINCT ON (application_id) " + COLUMNS
				+ " FROM hiring.hire WHERE application_id = ANY(:ids) ORDER BY application_id, started_at DESC, id DESC")
				.param("ids", applicationIds.toArray(UUID[]::new)).query(HireStore::map).list();
	}

	private static StoredHire map(ResultSet rs, int row) throws SQLException {
		return new StoredHire(rs.getObject("id", UUID.class), rs.getObject("gig_id", UUID.class),
				rs.getObject("application_id", UUID.class), rs.getObject("talent_id", UUID.class),
				rs.getObject("poster_id", UUID.class), HireSide.valueOf(rs.getString("started_by")), rs.getString("status"),
				instant(rs, "started_at"), instant(rs, "expires_at"), instant(rs, "confirmed_at"), instant(rs, "ended_at"),
				rs.getString("cancelled_by") == null ? null : HireSide.valueOf(rs.getString("cancelled_by")),
				rs.getString("cancel_reason"), rs.getObject("booking_id", UUID.class));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

}
