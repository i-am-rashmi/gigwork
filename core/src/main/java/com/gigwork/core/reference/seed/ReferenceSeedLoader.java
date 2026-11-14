package com.gigwork.core.reference.seed;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Writes the seed files to the {@code reference} schema. Every statement is an upsert by the stable key, so running
 * a load again changes nothing. A conflict updates the descriptive columns but never {@code status}: retiring a row
 * is a decision made later, and a reload must not undo it.
 */
final class ReferenceSeedLoader {

	private static final Logger log = LoggerFactory.getLogger(ReferenceSeedLoader.class);

	private ReferenceSeedLoader() {
	}

	// ---- categories, specialisations, languages ------------------------------------------------------------

	static void loadReference(JdbcTemplate jdbc, SeedFiles files) {
		List<Map<String, String>> categories = read(files, "categories.csv");
		List<Map<String, String>> specialisations = read(files, "specialisations.csv");
		List<Map<String, String>> languages = read(files, "languages.csv");

		Set<String> categorySlugs = new HashSet<>();
		batch(jdbc, """
				INSERT INTO reference.category (id, slug, name, work_mode, language_relevant, display_order)
				VALUES (?, ?, ?, ?, ?, ?)
				ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name, work_mode = EXCLUDED.work_mode,
				    language_relevant = EXCLUDED.language_relevant, display_order = EXCLUDED.display_order
				""", categories, (ps, row) -> {
			String slug = required(row, "slug", "categories.csv");
			if (!categorySlugs.add(slug)) {
				throw new SeedDataException("categories.csv: duplicate slug " + slug);
			}
			ps.setObject(1, SeedIds.category(slug));
			ps.setString(2, slug);
			ps.setString(3, required(row, "name", "categories.csv"));
			ps.setString(4, required(row, "work_mode", "categories.csv"));
			ps.setBoolean(5, bool(row, "language_relevant", "categories.csv"));
			ps.setInt(6, integer(row, "display_order", "categories.csv"));
		});

		Set<String> specialisationKeys = new HashSet<>();
		batch(jdbc, """
				INSERT INTO reference.specialisation (id, category_id, slug, name, display_order)
				VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (category_id, slug) DO UPDATE SET name = EXCLUDED.name, display_order = EXCLUDED.display_order
				""", specialisations, (ps, row) -> {
			String category = required(row, "category_slug", "specialisations.csv");
			String slug = required(row, "slug", "specialisations.csv");
			if (!categorySlugs.contains(category)) {
				throw new SeedDataException("specialisations.csv: unknown category " + category + " for " + slug);
			}
			if (!specialisationKeys.add(category + "/" + slug)) {
				throw new SeedDataException("specialisations.csv: duplicate " + category + "/" + slug);
			}
			ps.setObject(1, SeedIds.specialisation(category, slug));
			ps.setObject(2, SeedIds.category(category));
			ps.setString(3, slug);
			ps.setString(4, required(row, "name", "specialisations.csv"));
			ps.setInt(5, integer(row, "display_order", "specialisations.csv"));
		});

		Set<String> codes = new HashSet<>();
		batch(jdbc, """
				INSERT INTO reference.language (code, name, display_order) VALUES (?, ?, ?)
				ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, display_order = EXCLUDED.display_order
				""", languages, (ps, row) -> {
			String code = required(row, "code", "languages.csv");
			if (!codes.add(code)) {
				throw new SeedDataException("languages.csv: duplicate code " + code);
			}
			ps.setString(1, code);
			ps.setString(2, required(row, "name", "languages.csv"));
			ps.setInt(3, integer(row, "display_order", "languages.csv"));
		});
		log.info("Reference seed: {} categories, {} specialisations, {} languages", categories.size(),
				specialisations.size(), languages.size());
	}

	// ---- one city -------------------------------------------------------------------------------------------

	/** A city's row in cities.csv. */
	record CityRow(String slug, String name, boolean live) {

		/** What the checksum hashes: only this city's row, so adding another city never invalidates this migration. */
		byte[] canonical() {
			return (slug + "|" + name + "|" + live).getBytes(StandardCharsets.UTF_8);
		}

	}

	static CityRow cityRow(SeedFiles files, String slug) {
		for (Map<String, String> row : read(files, "cities.csv")) {
			if (slug.equals(row.get("slug"))) {
				return new CityRow(slug, required(row, "name", "cities.csv"), bool(row, "live", "cities.csv"));
			}
		}
		throw new SeedDataException("cities.csv has no row for city '" + slug + "'");
	}

	static void loadCity(JdbcTemplate jdbc, SeedFiles files, String slug) {
		CityRow city = cityRow(files, slug);
		UUID cityId = SeedIds.city(slug);
		jdbc.update("""
				INSERT INTO reference.city (id, slug, name, status) VALUES (?, ?, ?, ?)
				ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name
				""", cityId, slug, city.name(), city.live() ? "LIVE" : "NOT_LIVE");

		// PostgreSQL parses the GeoJSON; one row per feature: its name and its geometry.
		String geojson = new String(files.read(slug + "/zones.geojson"), StandardCharsets.UTF_8);
		List<Map<String, Object>> zones = jdbc.queryForList("""
				SELECT f -> 'properties' ->> 'name' AS name, (f -> 'geometry')::text AS geometry
				FROM jsonb_array_elements(CAST(? AS jsonb) -> 'features') AS f
				""", geojson);
		if (zones.isEmpty()) {
			throw new SeedDataException(slug + "/zones.geojson has no zones");
		}
		Set<String> zoneNames = new HashSet<>();
		for (Map<String, Object> zone : zones) {
			String name = (String) zone.get("name");
			if (name == null || name.isBlank() || !zoneNames.add(name)) {
				throw new SeedDataException(slug + "/zones.geojson: a zone has no name or its name is repeated");
			}
			jdbc.update("""
					INSERT INTO reference.zone (id, city_id, name, boundary)
					VALUES (?, ?, ?, ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(?), 4326)))
					ON CONFLICT (city_id, name) DO UPDATE SET boundary = EXCLUDED.boundary
					""", SeedIds.zone(slug, name), cityId, name, zone.get("geometry"));
		}

		// The zone is computed by PostGIS: the one covering the point (lowest id on a shared border), else the nearest.
		List<Map<String, String>> localities = read(files, slug + "/localities.csv");
		Set<String> sources = new HashSet<>();
		batch(jdbc, """
				INSERT INTO reference.locality (id, source_id, city_id, zone_id, name, pin, lat, lng)
				VALUES (?, ?, ?,
				    COALESCE(
				        (SELECT z.id FROM reference.zone z WHERE z.city_id = ?
				            AND ST_Covers(z.boundary, ST_SetSRID(ST_MakePoint(?, ?), 4326)) ORDER BY z.id LIMIT 1),
				        (SELECT z.id FROM reference.zone z WHERE z.city_id = ?
				            ORDER BY z.boundary <-> ST_SetSRID(ST_MakePoint(?, ?), 4326), z.id LIMIT 1)),
				    ?, ?, ?, ?)
				ON CONFLICT (city_id, source_id) DO UPDATE SET zone_id = EXCLUDED.zone_id, name = EXCLUDED.name,
				    pin = EXCLUDED.pin, lat = EXCLUDED.lat, lng = EXCLUDED.lng
				""", localities, (ps, row) -> {
			String file = slug + "/localities.csv";
			String source = required(row, "source_id", file);
			if (!sources.add(source)) {
				throw new SeedDataException(file + ": duplicate source_id " + source);
			}
			double lat = decimal(row, "lat", file);
			double lng = decimal(row, "lng", file);
			String pin = row.get("pin").trim();
			ps.setObject(1, SeedIds.locality(slug, source));
			ps.setString(2, source);
			ps.setObject(3, cityId);
			ps.setObject(4, cityId);
			ps.setDouble(5, lng);
			ps.setDouble(6, lat);
			ps.setObject(7, cityId);
			ps.setDouble(8, lng);
			ps.setDouble(9, lat);
			ps.setString(10, required(row, "name", file));
			ps.setString(11, pin.isEmpty() ? null : pin);
			ps.setDouble(12, lat);
			ps.setDouble(13, lng);
		});
		log.info("Reference seed for {}: {} zones, {} localities", slug, zones.size(), localities.size());
	}

	// ---- helpers --------------------------------------------------------------------------------------------

	@FunctionalInterface
	private interface RowBinder {

		void bind(PreparedStatement ps, Map<String, String> row) throws SQLException;

	}

	private static void batch(JdbcTemplate jdbc, String sql, List<Map<String, String>> rows, RowBinder binder) {
		jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {
				binder.bind(ps, rows.get(i));
			}

			@Override
			public int getBatchSize() {
				return rows.size();
			}
		});
	}

	private static List<Map<String, String>> read(SeedFiles files, String name) {
		return Csv.parse(files.read(name), name);
	}

	private static String required(Map<String, String> row, String column, String file) {
		String value = row.get(column);
		if (value == null || value.isBlank()) {
			throw new SeedDataException(file + ": missing value for '" + column + "' in row " + row);
		}
		return value.trim();
	}

	private static boolean bool(Map<String, String> row, String column, String file) {
		String value = required(row, column, file);
		if (!value.equals("true") && !value.equals("false")) {
			throw new SeedDataException(file + ": '" + column + "' must be true or false, got '" + value + "'");
		}
		return Boolean.parseBoolean(value);
	}

	private static int integer(Map<String, String> row, String column, String file) {
		try {
			return Integer.parseInt(required(row, column, file));
		}
		catch (NumberFormatException e) {
			throw new SeedDataException(file + ": '" + column + "' must be an integer in row " + row, e);
		}
	}

	private static double decimal(Map<String, String> row, String column, String file) {
		try {
			return Double.parseDouble(required(row, column, file));
		}
		catch (NumberFormatException e) {
			throw new SeedDataException(file + ": '" + column + "' must be a number in row " + row, e);
		}
	}

}
