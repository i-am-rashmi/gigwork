package com.gigwork.core.reference.config;

import org.flywaydb.core.api.migration.JavaMigration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gigwork.core.reference.seed.ClasspathSeedFiles;
import com.gigwork.core.reference.seed.SeedMigration;

/** Registers the seed migrations listed in {@code db/seed-migrations.csv}, reading the seed files from the classpath. */
@Configuration(proxyBeanMethods = false)
class SeedMigrationConfig {

	@Bean
	FlywayConfigurationCustomizer seedMigrations() {
		return flyway -> flyway.javaMigrations(SeedMigration.fromManifest(new ClasspathSeedFiles())
				.toArray(new JavaMigration[0]));
	}

}
