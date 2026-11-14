package com.gigwork.core.profile.domain;

/** Specialisations are unknown, retired, repeated, too many or not in the chosen categories. */
public class InvalidSpecialisationsException extends InvalidProfileException {

	public InvalidSpecialisationsException(String message) {
		super(message);
	}

}
