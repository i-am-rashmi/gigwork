package com.gigwork.core.profile;

import java.util.Set;
import java.util.UUID;

/**
 * What Hiring needs to decide whether a Talent may apply (FR-23).
 *
 * @param status INCOMPLETE, ACTIVE or HIDDEN
 * @param categoryIds the categories on the profile; specialisations and languages are deliberately not included
 */
public record TalentEligibility(String status, Set<UUID> categoryIds) {

	public boolean isActive() {
		return "ACTIVE".equals(status);
	}

}
