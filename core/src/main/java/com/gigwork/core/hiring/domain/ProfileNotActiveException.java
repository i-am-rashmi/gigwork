package com.gigwork.core.hiring.domain;

/** The Talent has no active profile, so cannot apply. Answered with 409. */
public class ProfileNotActiveException extends RuntimeException {

	public ProfileNotActiveException() {
		super("Complete your Talent profile and make it visible before you apply.");
	}

}
