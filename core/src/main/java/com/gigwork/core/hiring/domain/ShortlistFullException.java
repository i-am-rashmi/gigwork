package com.gigwork.core.hiring.domain;

/** The gig already has the most shortlisted Talent allowed (NFR-38). Answered with 409. */
public class ShortlistFullException extends RuntimeException {

	public ShortlistFullException() {
		super("You've shortlisted the most Talent allowed for this gig. Remove someone before adding another.");
	}

}
