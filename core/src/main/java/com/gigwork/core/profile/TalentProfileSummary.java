package com.gigwork.core.profile;

import java.util.UUID;

/**
 * The little other modules need to know about a Talent's profile.
 *
 * @param status INCOMPLETE, ACTIVE or HIDDEN
 * @param cityId the city of the saved home location, or null while no location is saved
 */
public record TalentProfileSummary(UUID accountId, String status, UUID cityId) {
}
