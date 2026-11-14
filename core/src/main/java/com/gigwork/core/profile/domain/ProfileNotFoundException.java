package com.gigwork.core.profile.domain;

import java.util.UUID;

/** A profile that should exist (it was just saved) is missing; or none exists for the account. */
public class ProfileNotFoundException extends RuntimeException {

	public ProfileNotFoundException(UUID accountId) {
		super("no talent profile for account " + accountId);
	}

}
