package com.gigwork.core.reference.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.gigwork.core.reference.support.ReferenceIntegrationTest;
import com.gigwork.core.reference.support.SeedFixtures;

/** The seed files, as loaded by Flyway when the application started, against the database. */
@ReferenceIntegrationTest
@Transactional
class ReferenceSeedLoadTest {

	@Autowired
	JdbcTemplate jdbc;

	private int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM reference." + table, Integer.class);
	}

	@Test
	void rowCountsMatchTheSeedFiles() {
		assertThat(count("category")).isEqualTo(SeedFixtures.rows("categories.csv"));
		assertThat(count("specialisation")).isEqualTo(SeedFixtures.rows("specialisations.csv"));
		assertThat(count("language")).isEqualTo(SeedFixtures.rows("languages.csv"));
		assertThat(count("city")).isEqualTo(SeedFixtures.rows("cities.csv"));
		assertThat(count("zone")).isEqualTo(SeedFixtures.zones());
		assertThat(count("locality")).isEqualTo(SeedFixtures.rows("bengaluru/localities.csv"));
	}

	@Test
	void idsAreTheDeterministicOnes() {
		assertThat(jdbc.queryForObject("SELECT id FROM reference.category WHERE slug = 'photographer'", UUID.class))
				.isEqualTo(SeedIds.category("photographer"));
		assertThat(jdbc.queryForObject("SELECT id FROM reference.city WHERE slug = 'bengaluru'", UUID.class))
				.isEqualTo(SeedIds.city("bengaluru"));
		assertThat(jdbc.queryForObject("SELECT id FROM reference.zone WHERE name = 'East'", UUID.class))
				.isEqualTo(SeedIds.zone("bengaluru", "East"));
		assertThat(jdbc.queryForObject("""
				SELECT id FROM reference.specialisation WHERE slug = 'wedding'
				AND category_id = ?""", UUID.class, SeedIds.category("photographer")))
						.isEqualTo(SeedIds.specialisation("photographer", "wedding"));
	}

	@Test
	void categoryFlagsAndModesComeFromTheFile() {
		assertThat(jdbc.queryForObject("SELECT language_relevant FROM reference.category WHERE slug = 'anchor'", Boolean.class)).isTrue();
		assertThat(jdbc.queryForObject("SELECT language_relevant FROM reference.category WHERE slug = 'photographer'", Boolean.class)).isFalse();
		assertThat(jdbc.queryForObject("SELECT work_mode FROM reference.category WHERE slug = 'video-editor'", String.class)).isEqualTo("REMOTE");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM reference.category WHERE status <> 'LIVE'", Integer.class)).isZero();
	}

	@Test
	void everyLocalityHasAZoneOfItsOwnCity() {
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM reference.locality l JOIN reference.zone z ON z.id = l.zone_id
				WHERE z.city_id <> l.city_id""", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM reference.locality WHERE zone_id IS NULL", Integer.class)).isZero();
	}

	@Test
	void aLocalityInsideAZoneGetsThatZone() {
		// every locality whose centre point is covered by a zone must have been given exactly such a zone
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM reference.locality l
				WHERE EXISTS (SELECT 1 FROM reference.zone z WHERE z.city_id = l.city_id AND ST_Covers(z.boundary, l.location))
				  AND NOT EXISTS (SELECT 1 FROM reference.zone z WHERE z.id = l.zone_id AND ST_Covers(z.boundary, l.location))
				""", Integer.class)).isZero();
	}

	@Test
	void zonesOfACityDoNotShareArea() {
		// tolerance 100 m2: the boundaries are simplified and rounded to 6 decimals
		List<String> overlaps = jdbc.queryForList("""
				SELECT a.name || ' / ' || b.name || ' ' || round(ST_Area(ST_Intersection(a.boundary, b.boundary)::geography)::numeric) || ' m2'
				FROM reference.zone a JOIN reference.zone b ON a.city_id = b.city_id AND a.id < b.id
				WHERE ST_Area(ST_Intersection(a.boundary, b.boundary)::geography) > 100
				""", String.class);

		assertThat(overlaps).as("zones that overlap").isEmpty();
	}

	@Test
	void loadingAgainChangesNothing() {
		List<String> before = jdbc.queryForList("""
				SELECT 'l' || id FROM reference.locality UNION ALL SELECT 'z' || id FROM reference.zone
				UNION ALL SELECT 's' || id FROM reference.specialisation UNION ALL SELECT 'c' || id FROM reference.category
				ORDER BY 1""", String.class);
		int localities = count("locality");

		ClasspathSeedFiles files = new ClasspathSeedFiles();
		ReferenceSeedLoader.loadReference(jdbc, files);
		ReferenceSeedLoader.loadCity(jdbc, files, "bengaluru");

		assertThat(count("locality")).isEqualTo(localities);
		assertThat(jdbc.queryForList("""
				SELECT 'l' || id FROM reference.locality UNION ALL SELECT 'z' || id FROM reference.zone
				UNION ALL SELECT 's' || id FROM reference.specialisation UNION ALL SELECT 'c' || id FROM reference.category
				ORDER BY 1""", String.class)).isEqualTo(before);
	}

	@Test
	void aReloadRepairsEditedRowsButKeepsTheirStatus() {
		jdbc.update("UPDATE reference.category SET name = 'Edited', status = 'RETIRED' WHERE slug = 'dj'");

		ReferenceSeedLoader.loadReference(jdbc, new ClasspathSeedFiles());

		assertThat(jdbc.queryForObject("SELECT name FROM reference.category WHERE slug = 'dj'", String.class)).isEqualTo("DJ");
		assertThat(jdbc.queryForObject("SELECT status FROM reference.category WHERE slug = 'dj'", String.class)).isEqualTo("RETIRED");
	}

}
