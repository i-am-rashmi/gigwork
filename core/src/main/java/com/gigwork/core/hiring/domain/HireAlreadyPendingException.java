package com.gigwork.core.hiring.domain;

/** The gig already has a pending hire. Answered with 409. */
public class HireAlreadyPendingException extends RuntimeException {

	public HireAlreadyPendingException() {
		super("A hire is already waiting for confirmation for this gig.");
	}

}
