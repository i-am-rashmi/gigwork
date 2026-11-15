package com.gigwork.core.hiring.domain;

/** The Talent has used all submissions allowed in the last 24 hours (NFR-39). Answered with 429. */
public class ApplyLimitReachedException extends RuntimeException {

	public ApplyLimitReachedException(int limit) {
		super("You can apply " + limit + " times in 24 hours, and you've reached that. Try again later.");
	}

}
