package com.gigwork.archfixtures.hygiene;

import org.springframework.beans.factory.annotation.Autowired;

/** Fixture for RuleCanaryTest: field injection. */
public class FieldInjectionBean {

	@Autowired
	private Object dependency;

	public Object dependency() {
		return dependency;
	}

}
