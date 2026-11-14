package com.gigwork.core.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.junit.jupiter.api.AfterEach;

import com.gigwork.core.reference.support.ReferenceIntegrationTest;

/**
 * The guarantees of the reference schema live in the database: each test breaks rules with SQL. Not transactional,
 * because PostgreSQL aborts a transaction after the first failed statement; the rows the tests add are removed after.
 */
@ReferenceIntegrationTest
class ReferenceSchemaTest {

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM reference.locality WHERE source_id LIKE 'node/9000%'").update();
		jdbc.sql("DELETE FROM reference.specialisation WHERE slug = 'newslug'").update();
		jdbc.sql("DELETE FROM reference.category WHERE slug IN ('brand-new', 'other-slug')").update();
	}

	private UUID city() {
		return jdbc.sql("SELECT id FROM reference.city WHERE slug = 'bengaluru'").query(UUID.class).single();
	}

	private UUID zone() {
		return jdbc.sql("SELECT id FROM reference.zone WHERE name = 'East'").query(UUID.class).single();
	}

	private int locality(String source, String name, String pin) {
		return jdbc.sql("""
				INSERT INTO reference.locality (id, source_id, city_id, zone_id, name, pin, lat, lng)
				VALUES (:id, :source, :city, :zone, :name, :pin, 12.97, 77.64)""")
				.param("id", UUID.randomUUID()).param("source", source).param("city", city()).param("zone", zone())
				.param("name", name).param("pin", pin).update();
	}

	private int category(String slug, String name) {
		return jdbc.sql("""
				INSERT INTO reference.category (id, slug, name, work_mode, language_relevant, display_order)
				VALUES (:id, :slug, :name, 'ON_SITE', false, 99)""")
				.param("id", UUID.randomUUID()).param("slug", slug).param("name", name).update();
	}

	@Test
	void localityNamesAreNotUniqueButSourceIdsAreWithinACity() {
		assertThat(locality("node/900001", "Twin Layout", "560001")).isEqualTo(1);
		assertThat(locality("node/900002", "Twin Layout", "560002")).isEqualTo(1);

		assertThatThrownBy(() -> locality("node/900001", "Another", "560003")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aPinIsSixDigitsOrAbsent() {
		assertThat(locality("node/900003", "No Pin Layout", null)).isEqualTo(1);

		assertThatThrownBy(() -> locality("node/900004", "Bad Pin", "56001")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> locality("node/900005", "Bad Pin 2", "060001")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void categorySlugsAndNamesAreUnique() {
		category("brand-new", "Brand new");

		assertThatThrownBy(() -> category("brand-new", "Other name")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> category("other-slug", "Brand new")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aSpecialisationSlugIsUniqueOnlyWithinItsCategory() {
		UUID photographer = jdbc.sql("SELECT id FROM reference.category WHERE slug = 'photographer'").query(UUID.class).single();
		UUID videographer = jdbc.sql("SELECT id FROM reference.category WHERE slug = 'videographer'").query(UUID.class).single();
		String insert = "INSERT INTO reference.specialisation (id, category_id, slug, name, display_order) VALUES (:id, :c, 'newslug', :n, 99)";

		jdbc.sql(insert).param("id", UUID.randomUUID()).param("c", photographer).param("n", "N1").update();
		jdbc.sql(insert).param("id", UUID.randomUUID()).param("c", videographer).param("n", "N2").update(); // same slug, other category

		assertThatThrownBy(() -> jdbc.sql(insert).param("id", UUID.randomUUID()).param("c", photographer).param("n", "N3").update())
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void citySlugsAreUniqueAndZoneNamesAreUniquePerCity() {
		assertThatThrownBy(() -> jdbc.sql("INSERT INTO reference.city (id, slug, name, status, time_zone) VALUES (:id, 'bengaluru', 'Other', 'LIVE', 'Asia/Kolkata')")
				.param("id", UUID.randomUUID()).update()).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.sql("""
				INSERT INTO reference.zone (id, city_id, name, boundary)
				SELECT :id, city_id, name, boundary FROM reference.zone WHERE name = 'East'""")
				.param("id", UUID.randomUUID()).update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aCityNeedsATimeZoneAndItIsNotBlank() {
		assertThatThrownBy(() -> jdbc.sql("INSERT INTO reference.city (id, slug, name, status) VALUES (:id, 'nozone', 'No zone', 'LIVE')")
				.param("id", UUID.randomUUID()).update()).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.sql("INSERT INTO reference.city (id, slug, name, status, time_zone) VALUES (:id, 'blank', 'Blank', 'LIVE', '  ')")
				.param("id", UUID.randomUUID()).update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void statusValuesAreRestricted() {
		assertThatThrownBy(() -> jdbc.sql("UPDATE reference.category SET status = 'DELETED' WHERE slug = 'dj'").update())
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.sql("UPDATE reference.city SET status = 'RETIRED' WHERE slug = 'bengaluru'").update())
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.sql("UPDATE reference.language SET status = 'x' WHERE code = 'en'").update())
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aZoneMustHaveAValidMultiPolygonBoundary() {
		assertThatThrownBy(() -> jdbc.sql("""
				INSERT INTO reference.zone (id, city_id, name, boundary)
				VALUES (:id, :city, 'Bow tie', ST_Multi(ST_GeomFromText('POLYGON((0 0, 1 1, 1 0, 0 1, 0 0))', 4326)))""")
				.param("id", UUID.randomUUID()).param("city", city()).update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void theLocationPointIsGeneratedFromLatAndLng() {
		locality("node/900006", "Point Layout", "560001");

		var point = jdbc.sql("SELECT ST_X(location) AS x, ST_Y(location) AS y FROM reference.locality WHERE source_id = 'node/900006'")
				.query((rs, n) -> new double[] { rs.getDouble("x"), rs.getDouble("y") }).single();
		assertThat(point).containsExactly(77.64, 12.97);
	}

}
