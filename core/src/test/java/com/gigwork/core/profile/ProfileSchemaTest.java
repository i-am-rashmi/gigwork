package com.gigwork.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.gigwork.core.reference.support.ReferenceIntegrationTest;

/**
 * The guarantees of the profile schema live in the database: each test breaks a rule with SQL. Not transactional,
 * because PostgreSQL aborts a transaction after the first failed statement; each test uses its own accounts and
 * removes them afterwards.
 */
@ReferenceIntegrationTest
class ProfileSchemaTest {

	@Autowired
	JdbcClient jdbc;

	private final UUID account = UUID.randomUUID();
	private final UUID categoryA = UUID.randomUUID();
	private final UUID categoryB = UUID.randomUUID();

	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM profile.talent_profile WHERE account_id = :a").param("a", account).update(); // cascades to the children
	}

	private void profile() {
		jdbc.sql("INSERT INTO profile.talent_profile (account_id, created_at, updated_at) VALUES (:a, now(), now())").param("a", account).update();
	}

	private int exec(String sql) {
		return jdbc.sql(sql).param("a", account).update();
	}

	private String status() {
		return jdbc.sql("SELECT status FROM profile.talent_profile WHERE account_id = :a").param("a", account).query(String.class).single();
	}

	private int count(String table) {
		return jdbc.sql("SELECT count(*) FROM profile." + table + " WHERE account_id = :a").param("a", account).query(Integer.class).single();
	}

	// ---- derived status ---------------------------------------------------------------------------------------------

	@Test
	void statusIsDerivedFromTheSectionsAndTheHiddenFlag() {
		profile();
		assertThat(status()).isEqualTo("INCOMPLETE");

		exec("UPDATE profile.talent_profile SET profile_type = 'INDIVIDUAL', headline = 'H' WHERE account_id = :a");
		assertThat(status()).isEqualTo("INCOMPLETE");

		exec("""
				UPDATE profile.talent_profile SET lat = 12.9, lng = 77.6, city_id = gen_random_uuid(), zone_id = gen_random_uuid(),
				location_label = 'X', service_radius_km = 5, willing_to_travel = false WHERE account_id = :a""");
		assertThat(status()).isEqualTo("ACTIVE");

		exec("UPDATE profile.talent_profile SET hidden = true WHERE account_id = :a");
		assertThat(status()).isEqualTo("HIDDEN");
		exec("UPDATE profile.talent_profile SET hidden = false WHERE account_id = :a");
		assertThat(status()).isEqualTo("ACTIVE");
	}

	@Test
	void statusCannotBeWrittenSoItCannotDisagreeWithTheData() {
		profile();

		assertThatThrownBy(() -> exec("UPDATE profile.talent_profile SET status = 'ACTIVE' WHERE account_id = :a"))
				.isInstanceOf(org.springframework.jdbc.BadSqlGrammarException.class);
	}

	@Test
	void theLocationPointIsGeneratedFromLatAndLng() {
		profile();
		exec("""
				UPDATE profile.talent_profile SET lat = 12.5, lng = 77.5, city_id = gen_random_uuid(), zone_id = gen_random_uuid(),
				location_label = 'X', service_radius_km = 5, willing_to_travel = false WHERE account_id = :a""");

		var xy = jdbc.sql("SELECT ST_X(location::geometry) AS x, ST_Y(location::geometry) AS y FROM profile.talent_profile WHERE account_id = :a")
				.param("a", account).query((rs, n) -> new double[] { rs.getDouble("x"), rs.getDouble("y") }).single();
		assertThat(xy).containsExactly(77.5, 12.5);
	}

	// ---- checks -----------------------------------------------------------------------------------------------------

	@Test
	void columnChecks() {
		profile();
		for (String bad : new String[] { "profile_type = 'TEAM', headline = 'H'", "profile_type = 'INDIVIDUAL', headline = ''",
				"profile_type = 'INDIVIDUAL', headline = repeat('x', 81)", "profile_type = 'INDIVIDUAL', headline = 'H', bio = repeat('x', 1001)",
				"profile_type = 'INDIVIDUAL', headline = 'H', member_count = 3", "profile_type = 'GROUP', headline = 'H', member_count = 1",
				"profile_type = 'GROUP', headline = 'H', member_count = 51" }) {
			assertThatThrownBy(() -> exec("UPDATE profile.talent_profile SET " + bad + " WHERE account_id = :a"))
					.as(bad).isInstanceOf(DataIntegrityViolationException.class);
		}
		exec("UPDATE profile.talent_profile SET profile_type = 'GROUP', headline = 'H', member_count = 2 WHERE account_id = :a");
		exec("UPDATE profile.talent_profile SET profile_type = 'GROUP', headline = 'H', member_count = 50 WHERE account_id = :a");
		exec("UPDATE profile.talent_profile SET profile_type = 'GROUP', headline = 'H', member_count = NULL WHERE account_id = :a");
	}

	@Test
	void eachSectionIsStoredWholeOrNotAtAll() {
		profile();

		assertThatThrownBy(() -> exec("UPDATE profile.talent_profile SET profile_type = 'INDIVIDUAL' WHERE account_id = :a"))
				.isInstanceOf(DataIntegrityViolationException.class); // type without headline
		assertThatThrownBy(() -> exec("UPDATE profile.talent_profile SET lat = 12.9, lng = 77.6 WHERE account_id = :a"))
				.isInstanceOf(DataIntegrityViolationException.class); // point without city, zone, label, radius
		assertThatThrownBy(() -> exec("UPDATE profile.talent_profile SET service_radius_km = 51, lat = 12.9, lng = 77.6, city_id = gen_random_uuid(), "
				+ "zone_id = gen_random_uuid(), location_label = 'X', willing_to_travel = true WHERE account_id = :a"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> exec("UPDATE profile.talent_profile SET lat = 95, lng = 77.6, city_id = gen_random_uuid(), zone_id = gen_random_uuid(), "
				+ "location_label = 'X', service_radius_km = 5, willing_to_travel = true WHERE account_id = :a"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void linkRules() {
		profile();
		String insert = "INSERT INTO profile.portfolio_link (id, account_id, url, type, display_order) VALUES (gen_random_uuid(), :a, '%s', '%s', %d)";

		exec(String.format(insert, "https://a.example.com", "WEBSITE", 0));

		assertThatThrownBy(() -> exec(String.format(insert, "https://a.example.com", "WEBSITE", 1))).isInstanceOf(DataIntegrityViolationException.class); // same url
		assertThatThrownBy(() -> exec(String.format(insert, "https://b.example.com", "WEBSITE", 0))).isInstanceOf(DataIntegrityViolationException.class); // same position
		assertThatThrownBy(() -> exec(String.format(insert, "https://b.example.com", "TIKTOK", 1))).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> exec(String.format(insert, "https://b.example.com", "WEBSITE", 10))).isInstanceOf(DataIntegrityViolationException.class);
	}

	// ---- children ---------------------------------------------------------------------------------------------------

	@Test
	void removingACategoryRemovesItsSpecialisationsInTheDatabase() {
		profile();
		UUID specOfA1 = UUID.randomUUID();
		UUID specOfA2 = UUID.randomUUID();
		UUID specOfB = UUID.randomUUID();
		exec("INSERT INTO profile.talent_category (account_id, category_id) VALUES (:a, '" + categoryA + "'), (:a, '" + categoryB + "')");
		exec("INSERT INTO profile.talent_specialisation (account_id, category_id, specialisation_id) VALUES (:a, '" + categoryA + "', '" + specOfA1
				+ "'), (:a, '" + categoryA + "', '" + specOfA2 + "'), (:a, '" + categoryB + "', '" + specOfB + "')");

		exec("DELETE FROM profile.talent_category WHERE account_id = :a AND category_id = '" + categoryA + "'");

		assertThat(jdbc.sql("SELECT specialisation_id FROM profile.talent_specialisation WHERE account_id = :a").param("a", account).query(UUID.class).list())
				.containsExactly(specOfB);
	}

	@Test
	void aSpecialisationNeedsItsCategoryOnTheSameProfile() {
		profile();
		exec("INSERT INTO profile.talent_category (account_id, category_id) VALUES (:a, '" + categoryA + "')");

		assertThatThrownBy(() -> exec("INSERT INTO profile.talent_specialisation (account_id, category_id, specialisation_id) VALUES (:a, '" + categoryB
				+ "', gen_random_uuid())")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aSpecialisationCanBelongToOneCategoryOnlyPerProfile() {
		profile();
		UUID spec = UUID.randomUUID();
		exec("INSERT INTO profile.talent_category (account_id, category_id) VALUES (:a, '" + categoryA + "'), (:a, '" + categoryB + "')");
		exec("INSERT INTO profile.talent_specialisation (account_id, category_id, specialisation_id) VALUES (:a, '" + categoryA + "', '" + spec + "')");

		assertThatThrownBy(() -> exec("INSERT INTO profile.talent_specialisation (account_id, category_id, specialisation_id) VALUES (:a, '" + categoryB
				+ "', '" + spec + "')")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void duplicateCategoriesAndLanguagesAreRefused() {
		profile();
		exec("INSERT INTO profile.talent_category (account_id, category_id) VALUES (:a, '" + categoryA + "')");
		exec("INSERT INTO profile.talent_language (account_id, language_code) VALUES (:a, 'kn')");

		assertThatThrownBy(() -> exec("INSERT INTO profile.talent_category (account_id, category_id) VALUES (:a, '" + categoryA + "')"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> exec("INSERT INTO profile.talent_language (account_id, language_code) VALUES (:a, 'kn')"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingAProfileDeletesEverythingBelowIt() {
		profile();
		exec("INSERT INTO profile.talent_category (account_id, category_id) VALUES (:a, '" + categoryA + "')");
		exec("INSERT INTO profile.talent_language (account_id, language_code) VALUES (:a, 'kn')");
		exec("INSERT INTO profile.portfolio_link (id, account_id, url, type, display_order) VALUES (gen_random_uuid(), :a, 'https://a.example.com', 'WEBSITE', 0)");

		exec("DELETE FROM profile.talent_profile WHERE account_id = :a");

		assertThat(count("talent_category") + count("talent_language") + count("portfolio_link")).isZero();
	}

	@Test
	void childRowsWithoutAProfileAreRefused() {
		assertThatThrownBy(() -> exec("INSERT INTO profile.talent_category (account_id, category_id) VALUES (:a, '" + categoryA + "')"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
