package com.gigwork.core.identity.domain;

/** The first name is empty, too long, has characters that are not part of a name, or contains contact details. */
public class InvalidFirstNameException extends RuntimeException {

	public enum Reason {
		FORMAT, CONTACT_DETAILS
	}

	private final Reason reason;

	public InvalidFirstNameException(Reason reason) {
		super("invalid first name: " + reason);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

}
