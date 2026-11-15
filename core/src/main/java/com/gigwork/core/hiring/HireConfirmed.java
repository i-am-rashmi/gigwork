package com.gigwork.core.hiring;

import java.time.Instant;
import java.util.UUID;

/** The other side confirmed the hire: the gig is filled and the Talent is booked (FR-27). Published in the transaction that changes the hire. */
public record HireConfirmed(UUID hireId, UUID applicationId, UUID gigId, UUID talentId, UUID posterId, UUID bookingId, Instant confirmedAt) {
}
