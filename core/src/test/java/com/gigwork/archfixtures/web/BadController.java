package com.gigwork.archfixtures.web;

import org.springframework.web.bind.annotation.RestController;

/** Fixture for RuleCanaryTest: a controller that uses a repository directly. */
@RestController
public class BadController {

	private final FixtureRepository repository;

	public BadController(FixtureRepository repository) {
		this.repository = repository;
	}

	public long count() {
		return repository.count();
	}

}
