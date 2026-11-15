package com.gigwork.core.profile;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A Talent's profile as a logged-in user sees it (FR-8, NFR-34): first name, categories, specialisations, languages,
 * headline, bio, type, member count, location label and city, and the portfolio links with their URLs. Never:
 * coordinates, radius, willing-to-travel, status or contact details.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicantProfile(UUID accountId, String firstName, String profileType, Integer memberCount,
		String headline, String bio, List<Named> categories, List<NamedSpecialisation> specialisations,
		List<Language> languages, Location location, List<Link> portfolio) {

	public record Named(UUID id, String slug, String name) {
	}

	public record NamedSpecialisation(UUID id, UUID categoryId, String slug, String name) {
	}

	public record Language(String code, String name) {
	}

	public record Location(String label, String cityName) {
	}

	public record Link(String type, String url) {
	}

}
