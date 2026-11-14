package com.gigwork.core.availability.domain;

/** The Talent already has the maximum number of upcoming busy blocks. */
public class BusyBlockLimitException extends RuntimeException {

	public BusyBlockLimitException(int limit) {
		super("You can have at most " + limit + " upcoming busy blocks. Remove one to add another.");
	}

}
