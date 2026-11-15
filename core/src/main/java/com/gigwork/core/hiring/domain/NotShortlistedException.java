package com.gigwork.core.hiring.domain;

/** The application is not currently shortlisted. Answered with 409. */
public class NotShortlistedException extends RuntimeException {

	public NotShortlistedException() {
		super("This Talent is not shortlisted.");
	}

}
