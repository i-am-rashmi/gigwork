package com.gigwork.core.gigs;

import java.time.Instant;
import java.util.UUID;

/**
 * The Poster cancelled an open or filled gig (FR-19).
 *
 * @param wasFilled true if a hire had filled the gig: Hiring then cancels the hire and releases the booking
 * @param reason the Poster's reason, or null
 */
public record GigCancelled(UUID gigId, UUID posterId, Instant cancelledAt, boolean wasFilled, String reason) {
}
