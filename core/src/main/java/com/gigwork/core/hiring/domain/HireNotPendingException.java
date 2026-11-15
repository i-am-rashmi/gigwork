package com.gigwork.core.hiring.domain;

/** The hire is not pending any more: it was confirmed, declined, withdrawn or lapsed, or it expired. Answered with 409. */
public class HireNotPendingException extends RuntimeException {

	public HireNotPendingException() {
		super("This hire is no longer waiting for confirmation.");
	}

}
