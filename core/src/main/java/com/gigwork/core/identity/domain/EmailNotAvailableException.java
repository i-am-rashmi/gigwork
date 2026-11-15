package com.gigwork.core.identity.domain;

/** Email can't be chosen as a contact method until email verification exists (FR-2). Answered with 400. */
public class EmailNotAvailableException extends RuntimeException {

	public EmailNotAvailableException() {
		super("Email can be shared once email verification is available.");
	}

}
