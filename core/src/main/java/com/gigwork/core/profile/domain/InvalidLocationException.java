package com.gigwork.core.profile.domain;

/** The location is missing, ambiguous, unknown or too far from any area we serve. */
public class InvalidLocationException extends InvalidProfileException {

	public InvalidLocationException(String message) {
		super(message);
	}

}
