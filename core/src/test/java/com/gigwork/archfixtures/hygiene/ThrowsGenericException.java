package com.gigwork.archfixtures.hygiene;

/** Fixture for RuleCanaryTest: throws a generic exception. */
public class ThrowsGenericException {

	public void run() {
		throw new RuntimeException("too generic");
	}

}
