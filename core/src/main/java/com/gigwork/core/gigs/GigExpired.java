package com.gigwork.core.gigs;

import java.time.Instant;
import java.util.UUID;

/** An open gig's start time passed without a hire (FR-31). */
public record GigExpired(UUID gigId, UUID posterId, Instant expiredAt) {
}
