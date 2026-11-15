package com.gigwork.core.profile;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read-only questions other modules may ask about Talent profiles. */
public interface TalentProfiles {

	/** The profile's summary, or empty if the account has never saved anything (any status counts as a profile). */
	Optional<TalentProfileSummary> summary(UUID accountId);

	/** The profile's status and category IDs, or empty if the account has never saved anything. One query. */
	Optional<TalentEligibility> eligibility(UUID accountId);

	/**
	 * For a Poster's list of applicants (FR-24): the profiles of these accounts as a logged-in user sees them, in a
	 * fixed number of queries however many IDs. ACTIVE and HIDDEN profiles are returned (a hidden profile is paused:
	 * not discoverable, but its Talent has chosen to apply); INCOMPLETE profiles and unknown accounts are absent.
	 * Use it only for Talent who have an active application on the Poster's gig; it is not a discovery API.
	 */
	Map<UUID, ApplicantProfile> applicantProfiles(Collection<UUID> accountIds);

}
