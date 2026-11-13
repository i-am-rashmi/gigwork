package com.gigwork.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Verifies the module structure of ADR-0004, including the absence of cycles. Needs no Docker. */
class ModularityTests {

	private static final List<String> EXPECTED_MODULES = List.of("identity", "profile", "availability", "reference",
			"gigs", "aidrafting", "hiring", "discovery", "platform", "moderation");

	private final ApplicationModules modules = ApplicationModules.of(CoreApplication.class);

	@Test
	void verifiesModuleStructure() {
		modules.verify();
	}

	@Test
	void detectsExactlyTheTenModules() {
		List<String> names = StreamSupport.stream(modules.spliterator(), false)
				.map(module -> module.getIdentifier().toString())
				.toList();

		assertThat(names).containsExactlyInAnyOrderElementsOf(EXPECTED_MODULES);
	}

}
