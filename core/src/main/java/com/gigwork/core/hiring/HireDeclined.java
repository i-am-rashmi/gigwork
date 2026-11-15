package com.gigwork.core.hiring;

import java.time.Instant;
import java.util.UUID;

/** The side that did not start a pending hire declined it. Published in the transaction that changes the hire. */
public record HireDeclined(UUID hireId, UUID applicationId, UUID gigId, UUID talentId, UUID posterId, HireSide startedBy, Instant declinedAt) {
}
