package com.gigwork.core.hiring;

import java.time.Instant;
import java.util.UUID;

/** A Talent withdrew an active application. Published in the transaction that changes the status. */
public record ApplicationWithdrawn(UUID applicationId, UUID gigId, UUID talentId, UUID posterId, Instant withdrawnAt) {
}
