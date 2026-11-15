package com.gigwork.core.hiring;

import java.time.Instant;
import java.util.UUID;

/** The side that started a pending hire withdrew it, or the gig was cancelled while it was pending. Published in the transaction that changes the hire. */
public record HireWithdrawn(UUID hireId, UUID applicationId, UUID gigId, UUID talentId, UUID posterId, HireSide startedBy, Instant withdrawnAt) {
}
