package com.gigwork.core.profile.domain;

/** A portfolio link is not acceptable. */
public class InvalidLinkException extends InvalidProfileException {

	public InvalidLinkException(String message) {
		super(message);
	}

}
