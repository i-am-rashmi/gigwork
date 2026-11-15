package com.gigwork.core.hiring.domain;

/** The hire is not confirmed, so there is nothing to cancel. Answered with 409. */
public class HireNotConfirmedException extends RuntimeException {

	public HireNotConfirmedException() {
		super("This hire is not confirmed.");
	}

}
