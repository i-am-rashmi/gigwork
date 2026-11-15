package com.gigwork.core.hiring.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gigwork.core.hiring.persistence.StoredHire;

/**
 * A hire as either of its two sides sees it. {@code expiresAt} matters while it is PENDING; {@code cancelledBy} and
 * {@code cancelReason} only after a cancellation. No contact details and no booking ID.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HireResponse(UUID id, UUID applicationId, UUID gigId, String status, String startedBy, Instant startedAt,
		Instant expiresAt, Instant confirmedAt, Instant endedAt, String cancelledBy, String cancelReason) {

	public static HireResponse of(StoredHire h) {
		return new HireResponse(h.id(), h.applicationId(), h.gigId(), h.status(), h.startedBy().name(), h.startedAt(),
				h.expiresAt(), h.confirmedAt(), h.endedAt(), h.cancelledBy() == null ? null : h.cancelledBy().name(),
				h.cancelReason());
	}

}
