package com.gigwork.core.gigs.persistence;

import java.time.Instant;
import java.util.UUID;

import com.gigwork.core.gigs.domain.GigStatus;
import com.gigwork.core.gigs.domain.WorkMode;

/**
 * A gig as stored. The exact point (lat, lng) is read only to compare it in updates; it is deliberately not part of
 * this record, so nothing built from it can ever return the coordinates (NFR-35).
 */
public record StoredGig(UUID id, UUID posterId, UUID categoryId, UUID specialisationId, String languageCode,
		String title, String description, Instant startAt, Instant endAt, WorkMode workMode, UUID cityId, UUID zoneId,
		String locationLabel, UUID localityId, Boolean taOffered, Long budgetMin, Long budgetMax, GigStatus status,
		Instant publishedAt, Instant updatedAt, Instant closedAt, String closeReason) {
}
