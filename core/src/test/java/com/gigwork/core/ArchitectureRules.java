package com.gigwork.core;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * The ArchUnit rules of ADR-0004 that can be checked today. Each rule allows an empty match because the
 * codebase is still small; {@code RuleCanaryTest} proves the rules fail on bad code.
 *
 * <p>Not yet checkable (follow-ups): only the platform module writes outbox events; schema-mapped entities are
 * used only from their module; no JPA relationships across modules; layering inside modules; native SQL scan.
 * Also: remove {@code allowEmptyShould(true)} from each rule once the modules it covers contain real classes,
 * so a rule that matches nothing is reported instead of silently passing.
 */
final class ArchitectureRules {

	private ArchitectureRules() {
	}

	/** No cycles between the top-level packages (modules) below {@code rootPackage}. */
	static ArchRule noModuleCycles(String rootPackage) {
		return slices().matching(rootPackage + ".(*)..")
				.should().beFreeOfCycles()
				.allowEmptyShould(true);
	}

	static final ArchRule CONTROLLERS_DONT_USE_REPOSITORIES = noClasses()
			.that().areAnnotatedWith(RestController.class)
			.or().areAnnotatedWith(Controller.class)
			.should().dependOnClassesThat(assignableTo(org.springframework.data.repository.Repository.class)
					.or(annotatedWith(Repository.class)))
			.because("controllers go through the module's services, never straight to persistence")
			.allowEmptyShould(true);

	static final ArchRule NO_FIELD_INJECTION = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION
			.allowEmptyShould(true);

	static final ArchRule NO_STANDARD_STREAMS = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
			.allowEmptyShould(true);

	static final ArchRule NO_GENERIC_EXCEPTIONS = GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS
			.allowEmptyShould(true);

}
