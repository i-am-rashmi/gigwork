package com.gigwork.core.gigs.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gigwork.core.gigs.domain.GigStatus;
import com.gigwork.core.gigs.domain.WorkMode;

/**
 * Gigs. Every status change and every edit is one conditional statement (ADR-0008), so a gig that was cancelled,
 * expired or started a moment ago can never be edited, and two simultaneous changes cannot both win. Native SQL
 * through JdbcClient: the point is a PostGIS column with no JPA mapping.
 */
@Repository
public class GigStore {

	static final String COLUMNS = """
			id, poster_id, category_id, specialisation_id, language_code, title, description, start_at, end_at,
			work_mode, city_id, zone_id, location_label, locality_id, ta_offered, budget_min, budget_max, status,
			published_at, updated_at, closed_at, close_reason""";

	private final JdbcClient jdbc;

	GigStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	static OffsetDateTime ts(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	/** What an edit changed, worked out inside the same statement from the row's values before and after. */
	public record Changes(boolean dateTime, boolean location, boolean matching) {
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void insert(UUID id, UUID posterId, GigFields f, Instant now) {
		jdbc.sql("""
				INSERT INTO gigs.gig (id, poster_id, category_id, specialisation_id, language_code, title, description,
				    start_at, end_at, work_mode, lat, lng, city_id, zone_id, location_label, locality_id, ta_offered,
				    budget_min, budget_max, status, published_at, updated_at)
				VALUES (:id, :poster, :category, :specialisation, :language, :title, :description,
				    :start, :end, :mode, :lat, :lng, :city, :zone, :label, :locality, :ta,
				    :budgetMin, :budgetMax, 'OPEN', :now, :now)""")
				.param("id", id).param("poster", posterId).params(values(f)).param("now", ts(now)).update();
	}

	/**
	 * Replaces the editable fields of the Poster's gig, if it is still OPEN, has not started, and is in the same
	 * category and work mode (both are fixed once published). The row is locked by the statement itself, so a
	 * simultaneous cancel or expiry either happened first (no row matches) or waits and then finds the gig no longer
	 * open.
	 *
	 * @return what changed, or empty when no row matched: the gig doesn't exist, isn't the Poster's, isn't open, has
	 *         started, or has another category or work mode
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Changes> update(UUID id, UUID posterId, GigFields f, Instant now) {
		return jdbc.sql("""
				WITH old AS (
				    SELECT id, start_at, end_at, lat, lng, city_id, zone_id, location_label, specialisation_id, language_code
				    FROM gigs.gig
				    WHERE id = :id AND poster_id = :poster AND status = 'OPEN' AND start_at > :now
				      AND category_id = :category AND work_mode = :mode
				    FOR UPDATE)
				UPDATE gigs.gig g SET specialisation_id = :specialisation, language_code = :language, title = :title,
				    description = :description, start_at = :start, end_at = :end, lat = :lat, lng = :lng, city_id = :city,
				    zone_id = :zone, location_label = :label, locality_id = :locality, ta_offered = :ta,
				    budget_min = :budgetMin, budget_max = :budgetMax, updated_at = :now
				FROM old WHERE g.id = old.id
				RETURNING
				    (old.start_at IS DISTINCT FROM g.start_at OR old.end_at IS DISTINCT FROM g.end_at) AS date_time,
				    (old.lat IS DISTINCT FROM g.lat OR old.lng IS DISTINCT FROM g.lng OR old.city_id IS DISTINCT FROM g.city_id
				        OR old.zone_id IS DISTINCT FROM g.zone_id OR old.location_label IS DISTINCT FROM g.location_label) AS location,
				    (old.specialisation_id IS DISTINCT FROM g.specialisation_id
				        OR old.language_code IS DISTINCT FROM g.language_code) AS matching""")
				.param("id", id).param("poster", posterId).params(values(f)).param("now", ts(now))
				.query((rs, row) -> new Changes(rs.getBoolean("date_time"), rs.getBoolean("location"), rs.getBoolean("matching")))
				.optional();
	}

	/** OPEN to CANCELLED for the Poster's own gig; false when no row matched (see {@link #update}). */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean cancel(UUID id, UUID posterId, String reason, Instant now) {
		return jdbc.sql("""
				UPDATE gigs.gig SET status = 'CANCELLED', closed_at = :now, updated_at = :now, close_reason = :reason
				WHERE id = :id AND poster_id = :poster AND status = 'OPEN'""")
				.param("id", id).param("poster", posterId).param("reason", reason).param("now", ts(now)).update() == 1;
	}

	public Optional<StoredGig> find(UUID id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM gigs.gig WHERE id = :id").param("id", id).query(GigStore::map)
				.optional();
	}

	/** The named parameters shared by the insert and the update (a HashMap: several values are null). */
	private static Map<String, Object> values(GigFields f) {
		Map<String, Object> v = new HashMap<>();
		v.put("category", f.categoryId());
		v.put("specialisation", f.specialisationId());
		v.put("language", f.languageCode());
		v.put("title", f.title());
		v.put("description", f.description());
		v.put("start", ts(f.startAt()));
		v.put("end", ts(f.endAt()));
		v.put("mode", f.workMode().name());
		v.put("lat", f.lat());
		v.put("lng", f.lng());
		v.put("city", f.cityId());
		v.put("zone", f.zoneId());
		v.put("label", f.locationLabel());
		v.put("locality", f.localityId());
		v.put("ta", f.taOffered());
		v.put("budgetMin", f.budgetMin());
		v.put("budgetMax", f.budgetMax());
		return v;
	}

	static StoredGig map(ResultSet rs, int row) throws SQLException {
		return new StoredGig(rs.getObject("id", UUID.class), rs.getObject("poster_id", UUID.class),
				rs.getObject("category_id", UUID.class), rs.getObject("specialisation_id", UUID.class),
				rs.getString("language_code"), rs.getString("title"), rs.getString("description"),
				instant(rs, "start_at"), instant(rs, "end_at"), WorkMode.valueOf(rs.getString("work_mode")),
				rs.getObject("city_id", UUID.class), rs.getObject("zone_id", UUID.class), rs.getString("location_label"),
				rs.getObject("locality_id", UUID.class), (Boolean) rs.getObject("ta_offered"),
				(Long) rs.getObject("budget_min"), (Long) rs.getObject("budget_max"),
				GigStatus.valueOf(rs.getString("status")), instant(rs, "published_at"), instant(rs, "updated_at"),
				instant(rs, "closed_at"), rs.getString("close_reason"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

}
