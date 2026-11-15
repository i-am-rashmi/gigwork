package com.gigwork.core.hiring.domain;

/** No such application or gig, or it is not the caller's to act on (never says which). Answered with 404. */
public class ApplicationNotFoundException extends RuntimeException {

	public ApplicationNotFoundException() {
		super("Not found.");
	}

}
