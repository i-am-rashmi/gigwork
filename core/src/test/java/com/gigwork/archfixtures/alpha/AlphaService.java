package com.gigwork.archfixtures.alpha;

import com.gigwork.archfixtures.beta.BetaService;

/** Fixture for RuleCanaryTest: alpha and beta depend on each other (a cycle). */
public class AlphaService {

	public BetaService beta() {
		return new BetaService();
	}

}
