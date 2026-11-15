package com.gigwork.core.hiring;

import java.time.Instant;
import java.util.UUID;

/**
 * A Poster shortlisted an applicant (FR-25), or added a removed one again. The only shortlist event that notifies
 * anyone: removal and every consent change are silent (FR-25a, FR-26). Published in the transaction that records
 * the shortlist.
 */
public record ApplicantShortlisted(UUID applicationId, UUID gigId, UUID posterId, UUID talentId,
		boolean posterSharesContact, Instant shortlistedAt) {
}
