package com.gigwork.archfixtures.web;

import org.springframework.data.repository.CrudRepository;

/** Fixture for RuleCanaryTest: a Spring Data repository. */
public interface FixtureRepository extends CrudRepository<Object, Long> {
}
