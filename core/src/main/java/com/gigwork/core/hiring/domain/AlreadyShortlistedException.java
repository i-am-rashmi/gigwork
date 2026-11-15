package com.gigwork.core.hiring.domain;

/** The Talent is already shortlisted for this gig. Answered with 409. */
public class AlreadyShortlistedException extends RuntimeException {

	public AlreadyShortlistedException() {
		super("This Talent is already shortlisted.");
	}

}
