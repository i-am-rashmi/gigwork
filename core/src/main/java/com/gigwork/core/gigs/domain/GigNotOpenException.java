package com.gigwork.core.gigs.domain;

/** The gig is no longer open (cancelled, filled or expired) or has already started, so it can't be changed (NFR-17). */
public class GigNotOpenException extends RuntimeException {

	public GigNotOpenException() {
		super("This gig is no longer open for changes: it was cancelled, filled or expired, or it has already started.");
	}

}
