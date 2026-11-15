package com.gigwork.core.hiring.persistence;

import java.time.Instant;
import java.util.UUID;

/** An application as stored. */
public record StoredApplication(UUID id, UUID gigId, UUID talentId, UUID posterId, String note, String status,
		Instant appliedAt, Instant withdrawnAt, boolean busyWarningShown, Instant createdAt) {

	public boolean isActive() {
		return "ACTIVE".equals(status);
	}

}
