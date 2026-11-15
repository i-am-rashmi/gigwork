package com.gigwork.core.hiring;

import java.time.Instant;
import java.util.UUID;

/** A Poster or a Talent started a hire (FR-27, FR-28); it waits for the other side until {@code expiresAt}. Published in the transaction that changes the hire. */
public record HireStarted(UUID hireId, UUID applicationId, UUID gigId, UUID talentId, UUID posterId, HireSide startedBy, Instant expiresAt, Instant startedAt) {
}
