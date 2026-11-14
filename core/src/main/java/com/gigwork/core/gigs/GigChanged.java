package com.gigwork.core.gigs;

import java.time.Instant;
import java.util.UUID;

/**
 * An open gig was edited (FR-18). The flags say what changed: applicants are told about a date, time or location
 * change, and matching runs again when the location or what is asked of the Talent changed. The other fields are the
 * gig's values after the change.
 *
 * @param dateTimeChanged the start or the end moved
 * @param locationChanged the place of an on-site gig changed
 * @param matchingChanged the specialisation or the language changed
 */
public record GigChanged(UUID gigId, UUID posterId, UUID categoryId, UUID specialisationId, String languageCode,
		String workMode, UUID cityId, UUID zoneId, Instant startAt, Instant endAt, boolean dateTimeChanged,
		boolean locationChanged, boolean matchingChanged, Instant changedAt) {
}
