package com.gigwork.core.profile.web.dto;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The location section (FR-5). Give exactly one of {@code localityId} (coordinates and label come from the reference
 * list) or {@code point} (stored rounded to 3 decimals).
 *
 * @param serviceRadiusKm 1 to 50
 */
public record LocationRequest(UUID localityId, @Valid Point point, @NotNull @Min(1) @Max(50) Integer serviceRadiusKm,
		@NotNull Boolean willingToTravel) {

	public record Point(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
			@NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {
	}

}
