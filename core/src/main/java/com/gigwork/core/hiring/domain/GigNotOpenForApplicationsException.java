package com.gigwork.core.hiring.domain;

/** The gig is not open, or has already started. Answered with 409. */
public class GigNotOpenForApplicationsException extends RuntimeException {

	public GigNotOpenForApplicationsException() {
		super("This gig is no longer open for applications.");
	}

}
