package com.gigwork.core.reference;

/** A chosen location is missing, ambiguous, unknown or too far from any area we serve. The message is safe to show. */
public class InvalidLocationException extends RuntimeException {

	public InvalidLocationException(String message) {
		super(message);
	}

}
