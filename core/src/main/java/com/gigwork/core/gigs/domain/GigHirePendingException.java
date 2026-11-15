package com.gigwork.core.gigs.domain;

/** A hire is waiting for confirmation, so the gig can't be edited until it is confirmed, declined, withdrawn or lapses. */
public class GigHirePendingException extends RuntimeException {

	public GigHirePendingException() {
		super("A hire is waiting for confirmation, so this gig can't be changed now. It can be edited again if the hire is declined, withdrawn or lapses.");
	}

}
