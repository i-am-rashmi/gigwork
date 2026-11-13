package com.gigwork.archfixtures.beta;

import com.gigwork.archfixtures.alpha.AlphaService;

/** Fixture for RuleCanaryTest: alpha and beta depend on each other (a cycle). */
public class BetaService {

	public AlphaService alpha() {
		return new AlphaService();
	}

}
