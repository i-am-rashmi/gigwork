package com.gigwork.core.gigs;

import java.time.Instant;
import java.util.UUID;

/** The Poster cancelled an open gig (FR-19). The reason is not part of the event. */
public record GigCancelled(UUID gigId, UUID posterId, Instant cancelledAt) {
}
