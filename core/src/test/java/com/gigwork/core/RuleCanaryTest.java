package com.gigwork.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Proves each rule in {@link ArchitectureRules} actually fails on bad code. The deliberately bad classes live in
 * {@code com.gigwork.archfixtures}, outside the core root package, so Modulith and the real rule run never see them.
 */
class RuleCanaryTest {

	private static final String FIXTURES = "com.gigwork.archfixtures";

	private final JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURES);

	@Test
	void fixturesAreFound() {
		assertThat(fixtures).isNotEmpty();
	}

	@Test
	void moduleCycleIsDetected() {
		assertFails(ArchitectureRules.noModuleCycles(FIXTURES), "alpha", "beta");
	}

	@Test
	void controllerUsingRepositoryIsDetected() {
		assertFails(ArchitectureRules.CONTROLLERS_DONT_USE_REPOSITORIES, "BadController");
	}

	@Test
	void fieldInjectionIsDetected() {
		assertFails(ArchitectureRules.NO_FIELD_INJECTION, "FieldInjectionBean");
	}

	@Test
	void systemOutIsDetected() {
		assertFails(ArchitectureRules.NO_STANDARD_STREAMS, "PrintsToStdout");
	}

	@Test
	void infrastructureEndpointsOutsidePlatformAreDetected() {
		assertFails(ArchitectureRules.ONLY_PLATFORM_DECLARES_INFRASTRUCTURE_ENDPOINTS,
				"DeclaresInfrastructureEndpoints");
	}

	@Test
	void genericExceptionIsDetected() {
		assertFails(ArchitectureRules.NO_GENERIC_EXCEPTIONS, "ThrowsGenericException");
	}

	private void assertFails(ArchRule rule, String... expectedInMessage) {
		var assertion = assertThatThrownBy(() -> rule.check(fixtures)).isInstanceOf(AssertionError.class);
		for (String part : expectedInMessage) {
			assertion.hasMessageContaining(part);
		}
	}

}
