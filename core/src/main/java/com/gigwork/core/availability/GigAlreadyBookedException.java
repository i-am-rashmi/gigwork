package com.gigwork.core.availability;

/** The gig already has an active booking, for another Talent or another period. A gig has at most one. */
public class GigAlreadyBookedException extends RuntimeException {

	public GigAlreadyBookedException(Throwable cause) {
		super("the gig already has an active booking", cause);
	}

}
