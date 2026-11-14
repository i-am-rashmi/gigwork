package com.gigwork.core.reference.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.gigwork.core.reference.AreaResolution;

/**
 * The one query that needs PostGIS at request time (a read; no invariant to protect, so a plain query rather than
 * JPA, ADR-0008). Only LIVE zones of LIVE cities are considered.
 */
@Repository
public class AreaRepository {

	private static final String LIVE_ZONES = """
			FROM reference.zone z JOIN reference.city c ON c.id = z.city_id
			WHERE z.status = 'LIVE' AND c.status = 'LIVE'
			""";

	private final JdbcClient jdbc;

	AreaRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** The zone covering the point; on a shared border the lowest zone id, so the answer never flips. */
	public Optional<AreaResolution> findCovering(double lat, double lng) {
		return jdbc.sql("SELECT z.city_id, z.id AS zone_id " + LIVE_ZONES
				+ " AND ST_Covers(z.boundary, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)) ORDER BY z.id LIMIT 1")
				.param("lng", lng).param("lat", lat)
				.query((rs, n) -> new AreaResolution(rs.getObject("city_id", UUID.class),
						rs.getObject("zone_id", UUID.class), true, 0))
				.optional();
	}

	/**
	 * The nearest zone (KNN on the GiST index; ranked by distance in degrees, which is fine for ordering the zones of
	 * one region) with its distance in metres.
	 */
	public Optional<AreaResolution> findNearest(double lat, double lng) {
		return jdbc.sql("SELECT z.city_id, z.id AS zone_id, ST_Distance(z.boundary::geography, "
				+ "ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS meters " + LIVE_ZONES
				+ " ORDER BY z.boundary <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), z.id LIMIT 1")
				.param("lng", lng).param("lat", lat)
				.query((rs, n) -> new AreaResolution(rs.getObject("city_id", UUID.class),
						rs.getObject("zone_id", UUID.class), false, rs.getDouble("meters")))
				.optional();
	}

}
