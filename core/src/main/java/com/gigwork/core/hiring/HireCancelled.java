package com.gigwork.core.hiring;

import java.time.Instant;
import java.util.UUID;

/** A confirmed hire was cancelled by one side, or because the gig was cancelled (FR-27a). The booking is released. Published in the transaction that changes the hire. */
public record HireCancelled(UUID hireId, UUID applicationId, UUID gigId, UUID talentId, UUID posterId, HireSide cancelledBy, String reason, Instant cancelledAt) {
}
