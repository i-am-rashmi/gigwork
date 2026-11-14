package com.gigwork.core.profile.web.dto;

import java.util.List;
import java.util.UUID;

import com.gigwork.core.profile.domain.ProfileType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The work section (FR-4). Saving it replaces the whole section.
 *
 * @param memberCount a group may give its size (2 to 50); an individual must leave it out
 * @param headline at most 80 characters, no contact details
 * @param bio at most 1000 characters, no contact details
 * @param categoryIds 1 to 5 live categories
 * @param specialisationIds up to 10 live specialisations, each of one of the chosen categories
 * @param languageCodes ISO 639 codes, allowed only if a chosen category is language-relevant
 */
public record WorkRequest(@NotNull ProfileType profileType, Integer memberCount, @NotBlank @Size(max = 80) String headline,
		@Size(max = 1000) String bio, @NotNull @Size(min = 1, max = 50) List<@NotNull UUID> categoryIds,
		@Size(max = 100) List<@NotNull UUID> specialisationIds, @Size(max = 50) List<@NotBlank @Size(max = 8) String> languageCodes) {
}
