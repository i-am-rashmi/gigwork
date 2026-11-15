package com.gigwork.core.hiring.domain;

/** The application has a pending or confirmed hire, so it can't be withdrawn and the Talent can't be removed from the shortlist. Answered with 409. */
public class HireInProgressException extends RuntimeException {

	public HireInProgressException() {
		super("End the hire first: this application has a pending or confirmed hire.");
	}

}
