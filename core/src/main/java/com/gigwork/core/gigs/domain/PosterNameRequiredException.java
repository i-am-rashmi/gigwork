package com.gigwork.core.gigs.domain;

/** A Poster needs a first name to publish a gig (FR-16). */
public class PosterNameRequiredException extends RuntimeException {

	public PosterNameRequiredException() {
		super("Add your first name before publishing a gig.");
	}

}
