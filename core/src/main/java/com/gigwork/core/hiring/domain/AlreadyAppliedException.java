package com.gigwork.core.hiring.domain;

/** The Talent already has an active application for the gig. Answered with 409. */
public class AlreadyAppliedException extends RuntimeException {

	public AlreadyAppliedException() {
		super("You've already applied to this gig.");
	}

}
