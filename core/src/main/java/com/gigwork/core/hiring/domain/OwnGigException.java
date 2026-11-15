package com.gigwork.core.hiring.domain;

/** A Poster cannot apply to their own gig. Answered with 409. */
public class OwnGigException extends RuntimeException {

	public OwnGigException() {
		super("You can't apply to your own gig.");
	}

}
