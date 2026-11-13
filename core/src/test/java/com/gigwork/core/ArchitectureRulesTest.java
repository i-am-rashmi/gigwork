package com.gigwork.core;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Runs the ADR-0004 ArchUnit rules against the production code of the core. */
@AnalyzeClasses(packages = "com.gigwork.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

	@ArchTest
	static final ArchRule noModuleCycles = ArchitectureRules.noModuleCycles("com.gigwork.core");

	@ArchTest
	static final ArchRule controllersDontUseRepositories = ArchitectureRules.CONTROLLERS_DONT_USE_REPOSITORIES;

	@ArchTest
	static final ArchRule noFieldInjection = ArchitectureRules.NO_FIELD_INJECTION;

	@ArchTest
	static final ArchRule noSystemOut = ArchitectureRules.NO_STANDARD_STREAMS;

	@ArchTest
	static final ArchRule onlyPlatformDeclaresInfrastructureEndpoints = ArchitectureRules.ONLY_PLATFORM_DECLARES_INFRASTRUCTURE_ENDPOINTS;

	@ArchTest
	static final ArchRule noGenericExceptions = ArchitectureRules.NO_GENERIC_EXCEPTIONS;

}
