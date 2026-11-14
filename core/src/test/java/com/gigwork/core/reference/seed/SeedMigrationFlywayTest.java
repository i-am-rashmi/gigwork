package com.gigwork.core.reference.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.flywaydb.core.api.migration.JavaMigration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.gigwork.core.reference.support.SeedFixtures;

/**
 * The seed migrations under real Flyway on fresh databases (not the Spring context): the same IDs everywhere, and a
 * seed file edited after its migration ran is caught by validation.
 */
class SeedMigrationFlywayTest {

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
	void aSeedFileEditedAfterItsMigrationRanFailsValidation(@TempDir Path seed) throws IOException {
		copy(SeedFixtures.SEED_DIR, seed);
		try (PostgreSQLContainer db = newDatabase()) {
			DataSource dataSource = dataSource(db);
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();
			flyway(dataSource, new DirectorySeedFiles(seed)).validate(); // untouched files validate

			// harmless for Git: only the line endings change, so the checksum must not
			Path categories = seed.resolve("categories.csv");
			Files.writeString(categories, Files.readString(categories).replace("\n", "\r\n"));
			flyway(dataSource, new DirectorySeedFiles(seed)).validate();

			Files.writeString(categories, Files.readString(categories).replace("Photographer", "Photographers"));

			assertThatThrownBy(() -> flyway(dataSource, new DirectorySeedFiles(seed)).validate())
					.isInstanceOf(FlywayValidateException.class).hasMessageContaining("checksum");
		}
	}

	@Test
	void editingACityFileInvalidatesOnlyThatCitysMigration(@TempDir Path seed) throws IOException {
		copy(SeedFixtures.SEED_DIR, seed);
		try (PostgreSQLContainer db = newDatabase()) {
			DataSource dataSource = dataSource(db);
			flyway(dataSource, new DirectorySeedFiles(seed)).migrate();

			// adding another city to cities.csv must not disturb the migrations already applied
			Files.writeString(seed.resolve("cities.csv"),
					Files.readString(seed.resolve("cities.csv")) + "pune,Pune,false\n");
			flyway(dataSource, new DirectorySeedFiles(seed)).validate();

			Files.writeString(seed.resolve("bengaluru/localities.csv"),
					Files.readString(seed.resolve("bengaluru/localities.csv")) + "node/1,Edited,560001,12.9,77.5\n");
			assertThatThrownBy(() -> flyway(dataSource, new DirectorySeedFiles(seed)).validate())
					.isInstanceOf(FlywayValidateException.class);
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
