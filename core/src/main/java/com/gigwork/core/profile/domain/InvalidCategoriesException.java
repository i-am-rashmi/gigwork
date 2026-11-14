package com.gigwork.core.profile.domain;

/** Categories are unknown, retired, repeated or too many. */
public class InvalidCategoriesException extends InvalidProfileException {

	public InvalidCategoriesException(String message) {
		super(message);
	}

}
