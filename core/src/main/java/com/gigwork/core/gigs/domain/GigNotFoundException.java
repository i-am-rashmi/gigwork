package com.gigwork.core.gigs.domain;

/** No such gig, or it is not the caller's to act on, or it is not visible to the caller (never says which). */
public class GigNotFoundException extends RuntimeException {

	public GigNotFoundException() {
		super("No such gig.");
	}

}
