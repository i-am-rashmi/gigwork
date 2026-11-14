package com.gigwork.core.reference.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidV5Test {

	/** The reference value of RFC 4122 / Python's uuid.uuid5(NAMESPACE_DNS, "python.org"). */
	@Test
	void matchesTheKnownVersion5Vector() {
		UUID dns = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

		assertThat(UuidV5.of(dns, "python.org")).hasToString("886313e1-3b8a-5372-9b90-0c9aee199e5d");
	}

	@Test
	void isVersion5AndStable() {
		UUID id = SeedIds.category("photographer");

		assertThat(id.version()).isEqualTo(5);
		assertThat(id.variant()).isEqualTo(2);
		assertThat(SeedIds.category("photographer")).isEqualTo(id);
	}

	@Test
	void keysOfDifferentKindsOrNamesNeverCollide() {
		assertThat(SeedIds.category("wedding")).isNotEqualTo(SeedIds.city("wedding"));
		assertThat(SeedIds.specialisation("photographer", "wedding")).isNotEqualTo(SeedIds.specialisation("videographer", "wedding"));
		assertThat(SeedIds.zone("bengaluru", "East")).isNotEqualTo(SeedIds.zone("bengaluru", "West"));
		assertThat(SeedIds.locality("bengaluru", "node/1")).isNotEqualTo(SeedIds.locality("bengaluru", "way/1"));
	}

	/** The IDs are data: if this fails the namespace or a name format changed, which would re-key every row. */
	@Test
	void idsNeverChange() {
		// computed independently with Python's uuid.uuid5 from the same namespace and names
		assertThat(SeedIds.category("photographer")).hasToString("3e173261-fbb7-537e-be1e-c4188c3f415b");
		assertThat(SeedIds.city("bengaluru")).hasToString("9c485ffa-b3d0-59d1-a180-e4cd466c0e85");
		assertThat(SeedIds.zone("bengaluru", "East")).hasToString("64f7b1cf-2cbb-5101-ab02-83399c02a76a");
		assertThat(SeedIds.specialisation("photographer", "wedding")).hasToString("48738b76-d8af-5716-8904-d696f391c6c5");
		assertThat(SeedIds.NAMESPACE).hasToString("ca6d29e2-6222-4d95-a77c-713f92e4a3a4");
	}

}
