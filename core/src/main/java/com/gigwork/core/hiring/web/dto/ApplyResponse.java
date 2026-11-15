package com.gigwork.core.hiring.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.gigwork.core.hiring.application.Applied;

/** Shown to the applicant only. {@code busyWarningShown} is theirs alone: a Poster never sees it. */
public record ApplyResponse(UUID id, UUID gigId, String status, Instant appliedAt, boolean busyWarningShown) {

	public static ApplyResponse of(Applied a) {
		return new ApplyResponse(a.id(), a.gigId(), "ACTIVE", a.appliedAt(), a.busyWarningShown());
	}

}
