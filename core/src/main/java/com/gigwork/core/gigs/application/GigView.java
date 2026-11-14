package com.gigwork.core.gigs.application;

import java.time.Instant;
import java.util.UUID;

import com.gigwork.core.gigs.domain.GigStatus;
import com.gigwork.core.gigs.domain.WorkMode;

/**
 * A gig as one viewer may see it (FR-9, NFR-34), built in the application layer so no endpoint can show more. There
 * is no point, no street address, no applicant count. {@code posterFirstName} is set only for logged-in viewers;
 * {@code updatedAt}, {@code closedAt} and {@code closeReason} only for the Poster.
 */
public record GigView(UUID id, String title, String description, UUID categoryId, String categoryName,
		UUID specialisationId, String specialisationName, String languageCode, String languageName, Instant startAt,
		Instant endAt, WorkMode workMode, String locationLabel, UUID cityId, String cityName, Boolean taOffered,
		Long budgetMin, Long budgetMax, GigStatus status, Instant publishedAt, String posterFirstName,
		Instant updatedAt, Instant closedAt, String closeReason) {
}
