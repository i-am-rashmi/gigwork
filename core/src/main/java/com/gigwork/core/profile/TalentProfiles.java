package com.gigwork.core.profile;

import java.util.Optional;
import java.util.UUID;

/** Read-only questions other modules may ask about Talent profiles. */
public interface TalentProfiles {

	/** The profile's summary, or empty if the account has never saved anything (any status counts as a profile). */
	Optional<TalentProfileSummary> summary(UUID accountId);

}
