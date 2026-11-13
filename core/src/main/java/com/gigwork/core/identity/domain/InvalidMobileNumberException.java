package com.gigwork.core.identity.domain;

/** The given text is not an Indian mobile number (NFR-37: Phase 1 sends OTPs to +91 numbers only). */
public class InvalidMobileNumberException extends RuntimeException {

	public InvalidMobileNumberException() {
		// deliberately no number in the message: mobile numbers never go into logs or errors (NFR-46)
		super("Not a valid Indian mobile number");
	}

}
