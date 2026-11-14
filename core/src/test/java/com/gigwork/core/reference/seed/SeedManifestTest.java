package com.gigwork.core.reference.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class SeedManifestTest {

	private static byte[] b(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void theRealManifestIsValidAndStartsAfterTheSqlMigrations() {
		var entries = SeedManifest.read();

		assertThat(entries).isNotEmpty();
		assertThat(entries.get(0).kind()).isEqualTo(SeedManifest.Kind.REFERENCE);
		assertThat(entries).filteredOn(e -> e.kind() == SeedManifest.Kind.CITY).extracting(SeedManifest.Entry::folder)
				.contains("bengaluru");
	}

	@Test
	void parsesKindsAndFolders() {
		var entries = SeedManifest.parse(b("version,description,kind,folder\n6,Ref,reference,.\n7,Pune,city,pune\n"));

		assertThat(entries).extracting(SeedManifest.Entry::kind).containsExactly(SeedManifest.Kind.REFERENCE, SeedManifest.Kind.CITY);
		assertThat(entries.get(1).folder()).isEqualTo("pune");
	}

	@Test
	void refusesARepeatedVersionAUnknownKindAndABadCityFolder() {
		assertThatThrownBy(() -> SeedManifest.parse(b("version,description,kind,folder\n6,A,reference,.\n6,B,city,pune\n")))
				.isInstanceOf(SeedDataException.class).hasMessageContaining("repeated");
		assertThatThrownBy(() -> SeedManifest.parse(b("version,description,kind,folder\n6,A,other,.\n")))
				.isInstanceOf(SeedDataException.class).hasMessageContaining("unknown kind");
		assertThatThrownBy(() -> SeedManifest.parse(b("version,description,kind,folder\n6,A,city,Pune City\n")))
				.isInstanceOf(SeedDataException.class).hasMessageContaining("slug");
	}

}
