package com.gigwork.core.gigs.domain;

/** A gig, or a request about gigs, breaks a rule. The message says which and never echoes what was submitted. */
public class InvalidGigException extends RuntimeException {

	public InvalidGigException(String message) {
		super(message);
	}

}
