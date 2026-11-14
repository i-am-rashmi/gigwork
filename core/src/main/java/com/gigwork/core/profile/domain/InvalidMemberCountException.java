package com.gigwork.core.profile.domain;

/** The member count does not fit the profile type. */
public class InvalidMemberCountException extends InvalidProfileException {

	public InvalidMemberCountException(String message) {
		super(message);
	}

}
