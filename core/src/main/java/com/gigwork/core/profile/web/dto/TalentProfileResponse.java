package com.gigwork.core.profile.web.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gigwork.core.profile.application.PublicProfile;

/**
 * A Talent's profile as others see it (FR-8, FR-9, NFR-34). Visitors get categories, specialisations, languages,
 * headline, bio, type, member count, the location label and city, and only the types of the portfolio links.
 * Logged-in users also get the first name (if set) and the links themselves. Never: coordinates, radius,
 * willing-to-travel, status or contact details.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TalentProfileResponse(UUID accountId, String firstName, String profileType, Integer memberCount,
		String headline, String bio, List<ProfileResponses.Named> categories,
		List<ProfileResponses.NamedSpecialisation> specialisations, List<ProfileResponses.Language> languages,
		Location location, List<Link> portfolio) {

	public record Location(String label, String cityName) {
	}

	/** {@code url} is present for logged-in users only. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Link(String type, String url) {
	}

	public static TalentProfileResponse of(PublicProfile p) {
		var v = p.profile();
		Location location = v.locationSaved() ? new Location(v.locationLabel(), v.cityName()) : null;
		List<Link> links = v.links().stream().map(l -> new Link(l.type(), p.loggedIn() ? l.url() : null)).toList();
		return new TalentProfileResponse(v.accountId(), p.firstName().orElse(null), v.profileType(), v.memberCount(),
				v.headline(), v.bio(), ProfileResponses.categories(v), ProfileResponses.specialisations(v),
				ProfileResponses.languages(v), location, links);
	}

}
