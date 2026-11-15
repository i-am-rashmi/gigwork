package com.gigwork.core.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * What other modules (Hiring) may ask Identity about the contact methods a user shares. Contact details are fetched
 * here at each view and never copied (FR-26).
 */
public interface ContactMethods {

	/** True if the account has chosen at least one method that it can actually share. */
	boolean hasContactMethod(UUID accountId);

	/** The chosen methods with the verified details, or empty if the account shares no usable method. */
	Optional<ContactCard> contactCard(UUID accountId);

}
