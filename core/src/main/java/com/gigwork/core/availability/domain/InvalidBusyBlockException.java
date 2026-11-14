package com.gigwork.core.availability.domain;

/** A busy block or a calendar request that breaks a rule (FR-7). The message is for the user. */
public class InvalidBusyBlockException extends RuntimeException {

	public InvalidBusyBlockException(String message) {
		super(message);
	}

}
