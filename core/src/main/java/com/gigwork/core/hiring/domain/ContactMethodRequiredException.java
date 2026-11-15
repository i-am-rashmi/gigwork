package com.gigwork.core.hiring.domain;

/** The caller has no contact method to share (FR-2). Answered with 409. */
public class ContactMethodRequiredException extends RuntimeException {

	public ContactMethodRequiredException() {
		super("Choose at least one contact method in your account first.");
	}

}
