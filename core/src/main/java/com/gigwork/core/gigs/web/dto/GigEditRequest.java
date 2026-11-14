package com.gigwork.core.gigs.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The full replacement of an open gig's editable fields (FR-18); a field left out is cleared. There is no category and
 * no work mode: they are fixed once the gig is published, and the gig's own are used to check this request (an
 * on-site gig needs a location and the travel-allowance flag, a remote one has neither).
 */
public record GigEditRequest(UUID specialisationId, @Size(max = 8) String languageCode,
		@NotBlank @Size(max = 100) String title, @NotBlank @Size(max = 2000) String description,
		@NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt, @Valid GigRequest.Location location,
		Boolean taOffered, BigDecimal budgetMin, BigDecimal budgetMax) {
}
