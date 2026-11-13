package com.gigwork.archfixtures.hygiene;

/** Fixture for RuleCanaryTest: writes to System.out. */
public class PrintsToStdout {

	public void run() {
		System.out.println("hello");
	}

}
