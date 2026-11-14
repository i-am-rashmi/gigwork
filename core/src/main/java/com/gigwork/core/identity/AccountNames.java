package com.gigwork.core.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only names of accounts for other modules. A first name is shown to logged-in users only; callers must apply
 * that rule themselves (Identity only answers who an account is).
 */
public interface AccountNames {

	/** The account's first name, or empty if the account has none or does not exist. */
	Optional<String> firstName(UUID accountId);

}
