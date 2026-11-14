package com.gigwork.core.gigs.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.gigwork.core.gigs.domain.WorkMode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A gig to publish, or the full replacement of an open gig's editable fields (a field left out is cleared).
 *
 * @param workMode ON_SITE or REMOTE; the category must allow it, and an edit can't change it
 * @param location on-site gigs only: give exactly one of {@code localityId} or {@code point} (stored rounded to
 *        3 decimals; no street address is collected)
 * @param taOffered on-site gigs only (required there): whether travel allowance is offered
 * @param budgetMin whole rupees; give both budget fields or neither (equal for a fixed amount)
 */
public record GigRequest(@NotNull UUID categoryId, UUID specialisationId, @Size(max = 8) String languageCode,
		@NotBlank @Size(max = 100) String title, @NotBlank @Size(max = 2000) String description,
		@NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt, @NotNull WorkMode workMode,
		@Valid Location location, Boolean taOffered, BigDecimal budgetMin, BigDecimal budgetMax) {

	public record Location(UUID localityId, @Valid Point point) {
	}

	public record Point(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
			@NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {
	}

}
