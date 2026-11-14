package com.gigwork.core.availability.domain;

/** Busy blocks belong to a Talent, so the account needs a profile (of any status) first. */
public class ProfileRequiredException extends RuntimeException {

	public ProfileRequiredException() {
		super("Set up your Talent profile before adding busy blocks.");
	}

}
