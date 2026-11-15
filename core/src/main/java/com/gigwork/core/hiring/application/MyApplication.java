package com.gigwork.core.hiring.application;

import java.time.Instant;
import java.util.UUID;

import com.gigwork.core.gigs.GigSummary;

/**
 * One of the caller's own applications with its gig. {@code gig} is the gig as it is now, including a closed one
 * (FILLED, CANCELLED, EXPIRED); it is null only if the gig has vanished, which Gigs never does.
 */
public record MyApplication(UUID id, UUID gigId, String status, String note, Instant appliedAt, Instant withdrawnAt,
		GigSummary gig) {
}
