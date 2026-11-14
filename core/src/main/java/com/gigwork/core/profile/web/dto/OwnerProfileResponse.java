package com.gigwork.core.profile.web.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gigwork.core.profile.application.ProfileView;

/**
 * Everything the owner may see of their own profile: no coordinates, only the location label. Sections not saved
 * yet are null (work, location) or empty.
 *
 * @param status INCOMPLETE until work and location are saved, then ACTIVE; HIDDEN while hidden
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OwnerProfileResponse(UUID accountId, String status, boolean hidden, Work work, Location location,
		List<Link> portfolio) {

	public record Work(String profileType, Integer memberCount, String headline, String bio,
			List<ProfileResponses.Named> categories, List<ProfileResponses.NamedSpecialisation> specialisations,
			List<ProfileResponses.Language> languages) {
	}

	public record Location(String label, UUID cityId, String cityName, UUID zoneId, String zoneName, UUID localityId,
			int serviceRadiusKm, boolean willingToTravel) {
	}

	public record Link(String url, String type) {
	}

	public static OwnerProfileResponse of(ProfileView v) {
		Work work = v.profileType() == null ? null : new Work(v.profileType(), v.memberCount(), v.headline(), v.bio(),
				ProfileResponses.categories(v), ProfileResponses.specialisations(v), ProfileResponses.languages(v));
		Location location = !v.locationSaved() ? null : new Location(v.locationLabel(), v.cityId(), v.cityName(),
				v.zoneId(), v.zoneName(), v.localityId(), v.serviceRadiusKm(), v.willingToTravel());
		return new OwnerProfileResponse(v.accountId(), v.status(), v.hidden(), work, location,
				v.links().stream().map(l -> new Link(l.url(), l.type())).toList());
	}

}
