package com.gigwork.core.hiring.domain;

/** The gig is not open, so nothing can be shortlisted. Answered with 409. */
public class GigNotOpenException extends RuntimeException {

	public GigNotOpenException() {
		super("This gig is no longer open.");
	}

}
