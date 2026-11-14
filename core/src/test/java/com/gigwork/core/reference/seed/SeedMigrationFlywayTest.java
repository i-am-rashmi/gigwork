package com.gigwork.core.reference.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.JavaMigration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.gigwork.core.reference.support.SeedFixtures;

/**
 * The seed migrations under real Flyway on real databases (not the Spring context). Fresh databases get the same IDs
 * everywhere. The upgrade path, which a fresh database cannot show, is the important part: seed files are repeatable
 * migrations, so a file edited after release reloads on the next start instead of breaking the start.
 */
class SeedMigrationFlywayTest {

	private static final String REFERENCE = "Reference categories specialisations languages";
	private static final String BENGALURU = "Seed city bengaluru";

	private static PostgreSQLContainer newDatabase() {
		PostgreSQLContainer container = new PostgreSQLContainer(
				DockerImageName.parse("imresamu/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));
		container.start();
		return container;
	}

	private static DataSource dataSource(PostgreSQLContainer db) {
		return new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
	}

	private static Flyway flyway(DataSource dataSource, SeedFiles files) {
		return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
				.javaMigrations(SeedMigration.fromManifest(files).toArray(new JavaMigration[0])).load();
	}

	private static List<String> allIds(DataSource dataSource) {
		return new JdbcTemplate(dataSource).queryForList("""
				SELECT 'category ' || id FROM reference.category UNION ALL
				SELECT 'specialisation ' || id FROM reference.specialisation UNION ALL
				SELECT 'language ' || code FROM reference.language UNION ALL
				SELECT 'city ' || id FROM reference.city UNION ALL
				SELECT 'zone ' || id FROM reference.zone UNION ALL
				SELECT 'locality ' || id || ' ' || zone_id FROM reference.locality ORDER BY 1""", String.class);
	}

	/** How many times each repeatable seed migration has been applied. */
	private static int applied(JdbcTemplate jdbc, String description) {
		return jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IS NULL AND description = ?",
				Integer.class, description);
	}

	private static String value(JdbcTemplate jdbc, String sql, Object... args) {
		return jdbc.queryForObject(sql, String.class, args);
	}

	// ---- fresh databases -----------------------------------------------------------------------------------

	@Test
	void twoFreshDatabasesGetTheSameIds() {
		try (PostgreSQLContainer first = newDatabase(); PostgreSQLContainer second = newDatabase()) {
			flyway(dataSource(first), new ClasspathSeedFiles()).migrate();
			flyway(dataSource(second), new ClasspathSeedFiles()).migrate();

			List<String> idsOfFirst = allIds(dataSource(first));

			assertThat(idsOfFirst).hasSizeGreaterThan(1000);
			assertThat(allIds(dataSource(second))).isEqualTo(idsOfFirst);
		}
	}

	@Test
	void seedMigrationsAreRepeatableAndRunOnlyWhenTheirFilesChange(@TempDir Path seed) throws IOException {
		copy(SeedFixtures.SEED_DIR, seed);
		try (PostgreSQLContainer db = newDatabase()) {
			DataSource dataSource = dataSource(db);
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();

			int sqlMigrations = jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL", Integer.class);
			assertThat(sqlMigrations).isGreaterThanOrEqualTo(5); // the versioned SQL migrations, whatever their number
			assertThat(applied(jdbc, REFERENCE)).isEqualTo(1);
			assertThat(applied(jdbc, BENGALURU)).isEqualTo(1);

			flyway(dataSource, new DirectorySeedFiles(seed)).migrate(); // nothing changed: nothing runs
			flyway(dataSource, new DirectorySeedFiles(seed)).validate();
			assertThat(applied(jdbc, REFERENCE)).isEqualTo(1);
			assertThat(applied(jdbc, BENGALURU)).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL", Integer.class)).isEqualTo(sqlMigrations);

			// only line endings change (Git can do that): the hash must not
			Path categories = seed.resolve("categories.csv");
			Files.writeString(categories, Files.readString(categories).replace("\n", "\r\n"));
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();
			assertThat(applied(jdbc, REFERENCE)).isEqualTo(1);
		}
	}

	@Test
	void addingAnotherCityRowDoesNotRerunTheMigrationsAlreadyApplied(@TempDir Path seed) throws IOException {
		copy(SeedFixtures.SEED_DIR, seed);
		try (PostgreSQLContainer db = newDatabase()) {
			DataSource dataSource = dataSource(db);
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();

			Files.writeString(seed.resolve("cities.csv"), Files.readString(seed.resolve("cities.csv")) + "pune,Pune,false,Asia/Kolkata\n");
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();

			assertThat(applied(jdbc, BENGALURU)).isEqualTo(1);
		}
	}

	@Test
	void aCitysTimeZoneComesFromTheSeedAndAnUnknownZoneFailsTheMigration(@TempDir Path seed) throws IOException {
		copy(SeedFixtures.SEED_DIR, seed);
		try (PostgreSQLContainer db = newDatabase()) {
			DataSource dataSource = dataSource(db);
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();
			assertThat(value(jdbc, "SELECT time_zone FROM reference.city WHERE slug = 'bengaluru'")).isEqualTo("Asia/Kolkata");

			Files.writeString(seed.resolve("cities.csv"), Files.readString(seed.resolve("cities.csv")).replace("Asia/Kolkata", "Asia/Kolkatta"));
			assertThatThrownBy(() -> flyway(dataSource, new DirectorySeedFiles(seed)).migrate()).hasStackTraceContaining("not a known time zone");

			Files.writeString(seed.resolve("cities.csv"), Files.readString(seed.resolve("cities.csv")).replace("Asia/Kolkatta", "Asia/Dubai"));
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate(); // an edited zone reloads the city migration
			assertThat(value(jdbc, "SELECT time_zone FROM reference.city WHERE slug = 'bengaluru'")).isEqualTo("Asia/Dubai");
		}
	}

	@Test
	void aMissingSeedFileFailsTheMigrationInsteadOfLoadingNothing(@TempDir Path seed) throws IOException {
		copy(SeedFixtures.SEED_DIR, seed);
		Files.delete(seed.resolve("specialisations.csv"));
		try (PostgreSQLContainer db = newDatabase()) {
			assertThatThrownBy(() -> flyway(dataSource(db), new DirectorySeedFiles(seed)).migrate())
					.hasStackTraceContaining("specialisations.csv");
		}
	}

	// ---- upgrade path: the database already exists and a seed file changes -----------------------------------

	@Test
	void editedSeedFilesReloadOnTheSameDatabase(@TempDir Path seed) throws IOException {
		copy(SeedFixtures.SEED_DIR, seed);
		try (PostgreSQLContainer db = newDatabase()) {
			DataSource dataSource = dataSource(db);
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();
			List<String> before = allIds(dataSource);
			int localitiesBefore = jdbc.queryForObject("SELECT count(*) FROM reference.locality", Integer.class);

			// the edits: a new category with a specialisation, another PIN, and a locality that disappears
			Path categories = seed.resolve("categories.csv");
			Path specialisations = seed.resolve("specialisations.csv");
			Path localities = seed.resolve("bengaluru/localities.csv");
			append(categories, "new-cat,New category,ON_SITE,true,99\n");
			append(specialisations, "new-cat,fresh,Fresh,1\n");
			List<String> lines = Files.readAllLines(localities);
			String changed = lines.stream().filter(l -> l.contains(",Indiranagar,")).findFirst().orElseThrow();
			String[] fields = changed.split(",");
			String sourceOfChanged = fields[0];
			String oldPin = fields[2];
			String newPin = oldPin.equals("560001") ? "560002" : "560001";
			String removed = lines.get(5);
			String sourceOfRemoved = removed.split(",")[0];
			lines.replaceAll(l -> l.equals(changed) ? fields[0] + "," + fields[1] + "," + newPin + "," + fields[3] + "," + fields[4] : l);
			lines.remove(removed);
			Files.write(localities, lines);

			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();

			// Flyway reloaded exactly the two seeds that changed
			assertThat(applied(jdbc, REFERENCE)).isEqualTo(2);
			assertThat(applied(jdbc, BENGALURU)).isEqualTo(2);
			// the new rows exist, with their deterministic IDs and in the right category
			assertThat(jdbc.queryForObject("SELECT id FROM reference.category WHERE slug = 'new-cat'", UUID.class))
					.isEqualTo(SeedIds.category("new-cat"));
			assertThat(jdbc.queryForObject("SELECT language_relevant FROM reference.category WHERE slug = 'new-cat'", Boolean.class)).isTrue();
			assertThat(jdbc.queryForObject("SELECT id FROM reference.specialisation WHERE slug = 'fresh'", UUID.class))
					.isEqualTo(SeedIds.specialisation("new-cat", "fresh"));
			// the PIN changed
			assertThat(value(jdbc, "SELECT pin FROM reference.locality WHERE source_id = ?", sourceOfChanged)).isEqualTo(newPin);
			// the removed locality is retired, not deleted
			assertThat(value(jdbc, "SELECT status FROM reference.locality WHERE source_id = ?", sourceOfRemoved)).isEqualTo("RETIRED");
			assertThat(jdbc.queryForObject("SELECT count(*) FROM reference.locality", Integer.class)).isEqualTo(localitiesBefore);
			// every other locality is still live, and no ID of a row that existed before has changed
			assertThat(jdbc.queryForObject("SELECT count(*) FROM reference.locality WHERE status = 'LIVE'", Integer.class))
					.isEqualTo(localitiesBefore - 1);
			Set<String> after = new HashSet<>(allIds(dataSource));
			assertThat(after).containsAll(before);
			assertThat(after.size() - before.size()).as("one category and one specialisation are new").isEqualTo(2);

			// a key that reappears is LIVE again
			Files.write(localities, Files.readAllLines(localities).stream().flatMap(l -> Stream.concat(Stream.of(l), l.equals(lines.get(4)) ? Stream.of(removed) : Stream.empty())).toList());
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();
			assertThat(value(jdbc, "SELECT status FROM reference.locality WHERE source_id = ?", sourceOfRemoved)).isEqualTo("LIVE");
			assertThat(applied(jdbc, BENGALURU)).isEqualTo(3);
		}
	}

	@Test
	void aCategoryRemovedFromTheFilesIsRetiredWithItsSpecialisationsAndComesBack(@TempDir Path seed) throws IOException {
		copy(SeedFixtures.SEED_DIR, seed);
		try (PostgreSQLContainer db = newDatabase()) {
			DataSource dataSource = dataSource(db);
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();
			int categories = jdbc.queryForObject("SELECT count(*) FROM reference.category", Integer.class);
			List<String> categoryLines = Files.readAllLines(seed.resolve("categories.csv"));
			List<String> specialisationLines = Files.readAllLines(seed.resolve("specialisations.csv"));

			Files.write(seed.resolve("categories.csv"), categoryLines.stream().filter(l -> !l.startsWith("magician,")).toList());
			Files.write(seed.resolve("specialisations.csv"), specialisationLines.stream().filter(l -> !l.startsWith("magician,")).toList());
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();

			assertThat(jdbc.queryForObject("SELECT count(*) FROM reference.category", Integer.class)).isEqualTo(categories);
			assertThat(value(jdbc, "SELECT status FROM reference.category WHERE slug = 'magician'")).isEqualTo("RETIRED");
			assertThat(jdbc.queryForObject("""
					SELECT count(*) FROM reference.specialisation s JOIN reference.category c ON c.id = s.category_id
					WHERE c.slug = 'magician' AND s.status = 'LIVE'""", Integer.class)).isZero();

			Files.write(seed.resolve("categories.csv"), categoryLines);
			Files.write(seed.resolve("specialisations.csv"), specialisationLines);
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();

			assertThat(value(jdbc, "SELECT status FROM reference.category WHERE slug = 'magician'")).isEqualTo("LIVE");
			assertThat(jdbc.queryForObject("""
					SELECT count(*) FROM reference.specialisation s JOIN reference.category c ON c.id = s.category_id
					WHERE c.slug = 'magician' AND s.status = 'LIVE'""", Integer.class)).isPositive();
		}
	}

	@Test
	void takingACityOffTheLiveListInCitiesCsvIsPickedUp(@TempDir Path seed) throws IOException {
		copy(SeedFixtures.SEED_DIR, seed);
		try (PostgreSQLContainer db = newDatabase()) {
			DataSource dataSource = dataSource(db);
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();
			assertThat(value(jdbc, "SELECT status FROM reference.city WHERE slug = 'bengaluru'")).isEqualTo("LIVE");

			Files.writeString(seed.resolve("cities.csv"), Files.readString(seed.resolve("cities.csv")).replace("bengaluru,Bengaluru,true,Asia/Kolkata", "bengaluru,Bengaluru,false,Asia/Kolkata"));
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();

			assertThat(value(jdbc, "SELECT status FROM reference.city WHERE slug = 'bengaluru'")).isEqualTo("NOT_LIVE");
		}
	}

	private static void append(Path file, String text) throws IOException {
		Files.writeString(file, Files.readString(file) + text);
	}

	private static void copy(Path from, Path to) throws IOException {
		try (Stream<Path> files = Files.walk(from)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				Path target = to.resolve(from.relativize(file).toString());
				Files.createDirectories(target.getParent());
				Files.copy(file, target);
			}
		}
	}

}
