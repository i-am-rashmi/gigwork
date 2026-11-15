package com.gigwork.core.gigs;

import java.time.Instant;
import java.util.UUID;

/**
 * The little other modules (Hiring) need to know about a gig. Deliberately no coordinates (NFR-35), no budget and
 * no description.
 *
 * @param workMode ON_SITE or REMOTE
 * @param locationLabel the locality or zone name; null for a remote gig
 */
public record GigSummary(UUID id, UUID posterId, UUID categoryId, String categoryName, String title, Instant startAt,
		Instant endAt, String workMode, String locationLabel, GigState status) {
}
