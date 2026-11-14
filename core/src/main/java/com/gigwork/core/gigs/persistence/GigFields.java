package com.gigwork.core.gigs.persistence;

import java.time.Instant;
import java.util.UUID;

import com.gigwork.core.gigs.domain.WorkMode;

/**
 * The editable content of a gig, already validated, as it is written. {@code lat}/{@code lng} and the other location
 * fields are all set for an on-site gig and all null for a remote one.
 */
public record GigFields(UUID categoryId, UUID specialisationId, String languageCode, String title, String description,
		Instant startAt, Instant endAt, WorkMode workMode, Double lat, Double lng, UUID cityId, UUID zoneId,
		String locationLabel, UUID localityId, Boolean taOffered, Long budgetMin, Long budgetMax) {
}
