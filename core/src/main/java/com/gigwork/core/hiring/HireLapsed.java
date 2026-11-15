package com.gigwork.core.hiring;

import java.time.Instant;
import java.util.UUID;

/** A pending hire passed its expiry, or its gig expired, without being confirmed. Published in the transaction that changes the hire. */
public record HireLapsed(UUID hireId, UUID applicationId, UUID gigId, UUID talentId, UUID posterId, HireSide startedBy, Instant lapsedAt) {
}
