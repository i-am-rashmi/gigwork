package com.gigwork.core.profile.application;

import java.util.List;
import java.util.UUID;

/**
 * A profile with the reference names filled in, ready to be shown. It never holds coordinates. Sections not saved
 * yet are empty or null.
 *
 * @param status INCOMPLETE, ACTIVE or HIDDEN
 */
public record ProfileView(UUID accountId, String status, boolean hidden, String profileType, Integer memberCount,
		String headline, String bio, List<Named> categories, List<NamedSpecialisation> specialisations,
		List<Language> languages, boolean locationSaved, String locationLabel, UUID cityId, String cityName, UUID zoneId,
		String zoneName, UUID localityId, Integer serviceRadiusKm, Boolean willingToTravel, List<Link> links) {

	public record Named(UUID id, String slug, String name) {
	}

	public record NamedSpecialisation(UUID id, UUID categoryId, String slug, String name) {
	}

	public record Language(String code, String name) {
	}

	public record Link(String url, String type) {
	}

}
