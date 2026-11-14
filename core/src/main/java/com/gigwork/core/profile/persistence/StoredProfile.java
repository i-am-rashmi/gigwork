package com.gigwork.core.profile.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A profile as stored. The coordinates are deliberately not part of it: nothing outside the database needs them in
 * Phase 1, and no API may return them (NFR-35). Sections not saved yet have null fields and empty lists.
 *
 * @param workSaved true once the work section was saved (then profileType and headline are set)
 * @param locationSaved true once the location section was saved
 * @param status INCOMPLETE, ACTIVE or HIDDEN, derived by the database
 */
public record StoredProfile(UUID accountId, String profileType, Integer memberCount, String headline, String bio,
		boolean locationSaved, UUID cityId, UUID zoneId, String locationLabel, UUID localityId, Integer serviceRadiusKm,
		Boolean willingToTravel, boolean hidden, String status, List<UUID> categoryIds,
		List<Specialisation> specialisations, List<String> languageCodes, List<Link> links, Instant updatedAt) {

	public record Specialisation(UUID categoryId, UUID specialisationId) {
	}

	public record Link(String url, String type) {
	}

	public boolean workSaved() {
		return profileType != null;
	}

	public boolean isActive() {
		return "ACTIVE".equals(status);
	}

}
