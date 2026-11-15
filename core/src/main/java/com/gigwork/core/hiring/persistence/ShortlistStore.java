package com.gigwork.core.hiring.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shortlist entries, the Poster's shortlist actions and the contact access log. Native SQL through JdbcClient
 * (ADR-0008): every change is one conditional statement. The two limits are invariants, so {@link #lockPoster} and
 * then {@link #lockGig} must be taken, in this order, in the same transaction before anything is counted.
 */
@Repository
public class ShortlistStore {

	/** A shortlist entry as stored. */
	public record Entry(UUID applicationId, UUID gigId, UUID posterId, UUID talentId, Instant shortlistedAt,
			Instant removedAt, boolean posterShares, Instant talentConsentedAt, Instant consentWithdrawnAt) {

		public boolean isCurrent() {
			return removedAt == null;
		}

		public boolean talentConsents() {
			return talentConsentedAt != null && consentWithdrawnAt == null;
		}

	}

	private static final String COLUMNS = """
			application_id, gig_id, poster_id, talent_id, shortlisted_at, removed_at, poster_shares, talent_consented_at,
			consent_withdrawn_at""";

	private final JdbcClient jdbc;

	ShortlistStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	private static OffsetDateTime ts(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private void advisoryLock(String key) {
		jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", key).query().singleRow();
	}

	/** Serialises all shortlist actions of one Poster (the 24-hour limit spans their gigs). Take it first. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockPoster(UUID posterId) {
		advisoryLock("shortlist_poster:" + posterId);
	}

	/** Serialises the shortlist changes of one gig (the limit of Talent shortlisted at once). Take it second. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockGig(UUID gigId) {
		advisoryLock("shortlist_gig:" + gigId);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public int countCurrent(UUID gigId) {
		return jdbc.sql("SELECT count(*) FROM hiring.shortlist_entry WHERE gig_id = :g AND removed_at IS NULL")
				.param("g", gigId).query(Integer.class).single();
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public int countActionsSince(UUID posterId, Instant since) {
		return jdbc.sql("SELECT count(*) FROM hiring.shortlist_action WHERE poster_id = :p AND at > :since")
				.param("p", posterId).param("since", ts(since)).query(Integer.class).single();
	}

	/**
	 * One statement: shortlists the Poster's ACTIVE application, or adds a removed Talent again, which resets their
	 * consent. The application row is locked FOR SHARE, so a withdrawal that arrives at the same moment waits for
	 * this transaction and then ends the shortlist. False if the Talent is already shortlisted or the application is
	 * not active.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean shortlist(UUID applicationId, UUID posterId, boolean posterShares, Instant now) {
		return jdbc.sql("""
				INSERT INTO hiring.shortlist_entry (application_id, gig_id, poster_id, talent_id, shortlisted_at,
				    removed_at, poster_shares, talent_consented_at, consent_withdrawn_at)
				SELECT a.id, a.gig_id, a.poster_id, a.talent_id, :now, NULL, :shares, NULL, NULL
				FROM hiring.application a
				WHERE a.id = :id AND a.poster_id = :poster AND a.status = 'ACTIVE'
				FOR SHARE OF a
				ON CONFLICT (application_id) DO UPDATE
				SET shortlisted_at = EXCLUDED.shortlisted_at, removed_at = NULL, poster_shares = EXCLUDED.poster_shares,
				    talent_consented_at = NULL, consent_withdrawn_at = NULL
				WHERE hiring.shortlist_entry.removed_at IS NOT NULL
				RETURNING application_id""")
				.param("id", applicationId).param("poster", posterId).param("shares", posterShares).param("now", ts(now))
				.query(UUID.class).optional().isPresent();
	}

	/** Removes a currently shortlisted Talent; false if they were not shortlisted. */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean remove(UUID applicationId, UUID posterId, Instant now) {
		return jdbc.sql("""
				UPDATE hiring.shortlist_entry SET removed_at = :now
				WHERE application_id = :id AND poster_id = :poster AND removed_at IS NULL""")
				.param("id", applicationId).param("poster", posterId).param("now", ts(now)).update() == 1;
	}

	/** Ends the shortlist of an application that was withdrawn; nothing to do if it was not shortlisted. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void endForWithdrawnApplication(UUID applicationId, Instant now) {
		jdbc.sql("UPDATE hiring.shortlist_entry SET removed_at = :now WHERE application_id = :id AND removed_at IS NULL")
				.param("id", applicationId).param("now", ts(now)).update();
	}

	/** The Poster's choice to share their contact, while the Talent is shortlisted; false if not shortlisted. */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean setPosterShares(UUID applicationId, UUID posterId, boolean share) {
		return jdbc.sql("""
				UPDATE hiring.shortlist_entry SET poster_shares = :share
				WHERE application_id = :id AND poster_id = :poster AND removed_at IS NULL""")
				.param("id", applicationId).param("poster", posterId).param("share", share).update() == 1;
	}

	/**
	 * The Talent's consent, while they are shortlisted. Consenting when already consenting changes nothing, and so
	 * does withdrawing when not consenting. False if the Talent is not shortlisted.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean setConsent(UUID applicationId, UUID talentId, boolean consent, Instant now) {
		String set = consent ? """
				talent_consented_at = CASE WHEN talent_consented_at IS NOT NULL AND consent_withdrawn_at IS NULL
				    THEN talent_consented_at ELSE :now END,
				consent_withdrawn_at = NULL"""
				: """
				consent_withdrawn_at = CASE WHEN talent_consented_at IS NOT NULL AND consent_withdrawn_at IS NULL
				    THEN :now ELSE consent_withdrawn_at END""";
		return jdbc.sql("UPDATE hiring.shortlist_entry SET " + set
				+ " WHERE application_id = :id AND talent_id = :talent AND removed_at IS NULL")
				.param("id", applicationId).param("talent", talentId).param("now", ts(now)).update() == 1;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void logAction(UUID posterId, UUID applicationId, String action, Instant now) {
		jdbc.sql("INSERT INTO hiring.shortlist_action (id, poster_id, application_id, action, at) VALUES (:id, :p, :a, :action, :now)")
				.param("id", UUID.randomUUID()).param("p", posterId).param("a", applicationId).param("action", action)
				.param("now", ts(now)).update();
	}

	/** One row per view of a contact; part of the view's own transaction, so a view that isn't logged isn't shown. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void logView(UUID viewerId, UUID viewedId, UUID gigId, UUID applicationId, Instant now) {
		jdbc.sql("""
				INSERT INTO hiring.contact_access_log (id, viewer_id, viewed_id, gig_id, application_id, viewed_at)
				VALUES (:id, :viewer, :viewed, :gig, :application, :now)""")
				.param("id", UUID.randomUUID()).param("viewer", viewerId).param("viewed", viewedId).param("gig", gigId)
				.param("application", applicationId).param("now", ts(now)).update();
	}

	/** The last entry of a page of the Shortlisted tab, to continue after it. */
	public record Keyset(Instant at, UUID id) {
	}

	/** Entries of many applications in one query (current or removed); applications never shortlisted are absent. */
	public List<Entry> findAll(Collection<UUID> applicationIds) {
		if (applicationIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("SELECT " + COLUMNS + " FROM hiring.shortlist_entry WHERE application_id = ANY(:ids)")
				.param("ids", applicationIds.toArray(UUID[]::new)).query(ShortlistStore::map).list();
	}

	/** The Talent's current entries, newest shortlisted first, after {@code after} when given. */
	public List<Entry> listCurrentByTalent(UUID talentId, Keyset after, int limit) {
		var sql = jdbc.sql("SELECT " + COLUMNS + " FROM hiring.shortlist_entry WHERE talent_id = :t AND removed_at IS NULL"
				+ (after == null ? "" : " AND (shortlisted_at, application_id) < (:atAt, :atId)")
				+ " ORDER BY shortlisted_at DESC, application_id DESC LIMIT :limit").param("t", talentId).param("limit", limit);
		if (after != null) {
			sql = sql.param("atAt", ts(after.at())).param("atId", after.id());
		}
		return sql.query(ShortlistStore::map).list();
	}

	public Optional<Entry> find(UUID applicationId) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM hiring.shortlist_entry WHERE application_id = :id")
				.param("id", applicationId).query(ShortlistStore::map).optional();
	}

	/**
	 * The entry with its row locked FOR SHARE, inside a view's transaction: a consent withdrawal or a removal at the
	 * same moment waits until the view has logged, so a logged view always saw a state that existed.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Entry> findForView(UUID applicationId) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM hiring.shortlist_entry WHERE application_id = :id FOR SHARE")
				.param("id", applicationId).query(ShortlistStore::map).optional();
	}

	private static Entry map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
		return new Entry(rs.getObject("application_id", UUID.class), rs.getObject("gig_id", UUID.class),
				rs.getObject("poster_id", UUID.class), rs.getObject("talent_id", UUID.class),
				instant(rs, "shortlisted_at"), instant(rs, "removed_at"), rs.getBoolean("poster_shares"),
				instant(rs, "talent_consented_at"), instant(rs, "consent_withdrawn_at"));
	}

	private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

}
