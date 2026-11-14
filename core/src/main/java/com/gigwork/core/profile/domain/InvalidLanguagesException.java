package com.gigwork.core.profile.domain;

/** Languages are unknown, retired or repeated, or no chosen category uses languages. */
public class InvalidLanguagesException extends InvalidProfileException {

	public InvalidLanguagesException(String message) {
		super(message);
	}

}
