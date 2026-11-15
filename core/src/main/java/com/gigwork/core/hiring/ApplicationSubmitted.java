package com.gigwork.core.hiring;

import java.time.Instant;
import java.util.UUID;

/**
 * A Talent applied to a gig, or applied again after withdrawing. Published in the transaction that records the
 * submission, so the event exists exactly when the submission does. Later the Poster's application summary
 * (FR-24) and analytics listen to it.
 *
 * @param reapply true when the Talent had withdrawn and the same application became active again
 */
public record ApplicationSubmitted(UUID applicationId, UUID gigId, UUID talentId, UUID posterId, boolean reapply,
		Instant submittedAt) {
}
