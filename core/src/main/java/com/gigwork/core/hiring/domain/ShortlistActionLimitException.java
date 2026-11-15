package com.gigwork.core.hiring.domain;

/** The Poster has made all the shortlist actions allowed in the last 24 hours (NFR-38). Answered with 429. */
public class ShortlistActionLimitException extends RuntimeException {

	public ShortlistActionLimitException(int limit) {
		super("You can add or remove shortlisted Talent " + limit + " times in 24 hours, and you've reached that. Try again later.");
	}

}
