package com.gigwork.core.profile.application;

import java.util.List;
import java.util.UUID;

import com.gigwork.core.profile.domain.ProfileType;

/** The work section of a profile (FR-4), as submitted. */
public record WorkCommand(ProfileType profileType, Integer memberCount, String headline, String bio,
		List<UUID> categoryIds, List<UUID> specialisationIds, List<String> languageCodes) {
}
