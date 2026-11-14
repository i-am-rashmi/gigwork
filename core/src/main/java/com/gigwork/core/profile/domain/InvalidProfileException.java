package com.gigwork.core.profile.domain;

/**
 * The request is well-formed but breaks a profile rule (FR-4 to FR-6a). The message is written for the user and
 * never echoes the submitted text (it may hold contact details).
 */
public class InvalidProfileException extends RuntimeException {

	public InvalidProfileException(String message) {
		super(message);
	}

}
