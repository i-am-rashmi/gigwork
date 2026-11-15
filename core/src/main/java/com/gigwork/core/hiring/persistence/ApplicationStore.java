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

/**
 * Applications and the submission log. Native SQL through JdbcClient (ADR-0008): every write that enforces an
 * invariant is one statement. The daily limit is an invariant, so {@link #lockTalent} must be called, in the same
 * transaction, before {@link #countSubmissionsSince} and {@link #submit}: it serialises one Talent's submissions.
 */
@Repository
public class ApplicationStore {

	private static final String COLUMNS = """
			id, gig_id, talent_id, poster_id, note, status, applied_at, withdrawn_at, busy_warning_shown, created_at""";

	/** The last application of a page, to continue after it. */
	public record Keyset(Instant appliedAt, UUID id) {
	}

	/** The row an apply wrote and whether it is a new application (false: a withdrawn one became active again). */
	public record Submitted(UUID id, boolean created) {
	}

	/** Who an application belongs to and where it is, for explaining why a conditional update matched nothing. */
	public record Owner(UUID talentId, String status) {
	}

	/** What a withdrawal changed, for the event. */
	public record Withdrawn(UUID gigId, UUID posterId) {
	}

	private final JdbcClient jdbc;

	ApplicationStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	static OffsetDateTime ts(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	/** Takes a per-Talent transaction-scoped advisory lock: one Talent's submissions wait for each other. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockTalent(UUID talentId) {
		jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", "apply:" + talentId).query()
				.singleRow();
	}

	/** Submissions of the Talent made after {@code since}, withdrawn or not. */
	@Transactional(propagation = Propagation.MANDATORY)
	public int countSubmissionsSince(UUID talentId, Instant since) {
		return jdbc.sql("SELECT count(*) FROM hiring.application_submission WHERE talent_id = :t AND submitted_at > :since")
				.param("t", talentId).param("since", ts(since)).query(Integer.class).single();
	}

	/**
	 * One statement: inserts the application, or makes a WITHDRAWN one active again; the unique (gig, Talent) key
	 * decides. Empty if the Talent already has an ACTIVE application (the update's WHERE matches no row).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Submitted> submit(UUID id, UUID gigId, UUID talentId, UUID posterId, String note, boolean busyShown,
			Instant now) {
		return jdbc.sql("""
				INSERT INTO hiring.application (id, gig_id, talent_id, poster_id, note, status, applied_at, withdrawn_at,
				    busy_warning_shown, created_at)
				VALUES (:id, :gig, :talent, :poster, :note, 'ACTIVE', :now, NULL, :busy, :now)
				ON CONFLICT (gig_id, talent_id) DO UPDATE
				SET status = 'ACTIVE', note = EXCLUDED.note, applied_at = EXCLUDED.applied_at, withdrawn_at = NULL,
				    busy_warning_shown = EXCLUDED.busy_warning_shown
				WHERE hiring.application.status = 'WITHDRAWN'
				RETURNING id, (xmax = 0) AS created""")
				.param("id", id).param("gig", gigId).param("talent", talentId).param("poster", posterId)
				.param("note", note).param("busy", busyShown).param("now", ts(now))
				.query((rs, i) -> new Submitted(rs.getObject("id", UUID.class), rs.getBoolean("created"))).optional();
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void logSubmission(UUID talentId, UUID applicationId, Instant now) {
		jdbc.sql("INSERT INTO hiring.application_submission (id, talent_id, application_id, submitted_at) "
				+ "VALUES (:id, :t, :a, :now)").param("id", UUID.randomUUID()).param("t", talentId)
				.param("a", applicationId).param("now", ts(now)).update();
	}

	/** Withdraws the Talent's ACTIVE application with one conditional update; empty if there was nothing to withdraw. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Withdrawn> withdraw(UUID id, UUID talentId, Instant now) {
		return jdbc.sql("""
				UPDATE hiring.application SET status = 'WITHDRAWN', withdrawn_at = :now
				WHERE id = :id AND talent_id = :t AND status = 'ACTIVE'
				RETURNING gig_id, poster_id""").param("id", id).param("t", talentId).param("now", ts(now))
				.query((rs, i) -> new Withdrawn(rs.getObject("gig_id", UUID.class), rs.getObject("poster_id", UUID.class)))
				.optional();
	}

	/**
	 * Locks the application row FOR SHARE and reads it afterwards, so the answer is what is committed now: a hire may
	 * start only for an application nobody is withdrawing at this moment (that needs FOR UPDATE and waits for us).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<StoredApplication> lockForShare(UUID id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM hiring.application WHERE id = :id FOR SHARE").param("id", id)
				.query(ApplicationStore::map).optional();
	}

	/**
	 * Locks the application row FOR UPDATE and reads it afterwards. Withdrawing an application and removing its Talent
	 * from the shortlist take this lock, then check for a hire in a separate statement (never inside the UPDATE, whose
	 * subquery would see the snapshot from before the wait), then change.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<StoredApplication> lockForUpdate(UUID id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM hiring.application WHERE id = :id FOR UPDATE").param("id", id)
				.query(ApplicationStore::map).optional();
	}

	public Optional<Owner> owner(UUID id) {
		return jdbc.sql("SELECT talent_id, status FROM hiring.application WHERE id = :id").param("id", id)
				.query((rs, i) -> new Owner(rs.getObject("talent_id", UUID.class), rs.getString("status"))).optional();
	}

	public Optional<StoredApplication> find(UUID id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM hiring.application WHERE id = :id").param("id", id)
				.query(ApplicationStore::map).optional();
	}

	/** A Talent's applications in any status, newest first (by latest submission), after {@code after} when given. */
	public List<StoredApplication> listByTalent(UUID talentId, Keyset after, int limit) {
		var sql = jdbc.sql("SELECT " + COLUMNS + " FROM hiring.application WHERE talent_id = :t"
				+ (after == null ? "" : " AND (applied_at, id) < (:atAt, :atId)")
				+ " ORDER BY applied_at DESC, id DESC LIMIT :limit").param("t", talentId).param("limit", limit);
		if (after != null) {
			sql = sql.param("atAt", ts(after.appliedAt())).param("atId", after.id());
		}
		return sql.query(ApplicationStore::map).list();
	}

	/** A gig's ACTIVE applications, oldest first, after {@code after} when given. */
	public List<StoredApplication> listActiveByGig(UUID gigId, Keyset after, int limit) {
		var sql = jdbc.sql("SELECT " + COLUMNS + " FROM hiring.application WHERE gig_id = :g AND status = 'ACTIVE'"
				+ (after == null ? "" : " AND (applied_at, id) > (:atAt, :atId)")
				+ " ORDER BY applied_at, id LIMIT :limit").param("g", gigId).param("limit", limit);
		if (after != null) {
			sql = sql.param("atAt", ts(after.appliedAt())).param("atId", after.id());
		}
		return sql.query(ApplicationStore::map).list();
	}

	private static StoredApplication map(ResultSet rs, int row) throws SQLException {
		return new StoredApplication(rs.getObject("id", UUID.class), rs.getObject("gig_id", UUID.class),
				rs.getObject("talent_id", UUID.class), rs.getObject("poster_id", UUID.class), rs.getString("note"),
				rs.getString("status"), instant(rs, "applied_at"), instant(rs, "withdrawn_at"),
				rs.getBoolean("busy_warning_shown"), instant(rs, "created_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

}
