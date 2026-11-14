package com.gigwork.core.profile.web.dto;

import java.util.List;
import java.util.UUID;

import com.gigwork.core.profile.application.ProfileView;

/** Parts shared by the owner's and the public profile responses. */
public final class ProfileResponses {

	private ProfileResponses() {
	}

	public record Named(UUID id, String slug, String name) {
	}

	public record NamedSpecialisation(UUID id, UUID categoryId, String slug, String name) {
	}

	public record Language(String code, String name) {
	}

	static List<Named> categories(ProfileView v) {
		return v.categories().stream().map(c -> new Named(c.id(), c.slug(), c.name())).toList();
	}

	static List<NamedSpecialisation> specialisations(ProfileView v) {
		return v.specialisations().stream().map(s -> new NamedSpecialisation(s.id(), s.categoryId(), s.slug(), s.name()))
				.toList();
	}

	static List<Language> languages(ProfileView v) {
		return v.languages().stream().map(l -> new Language(l.code(), l.name())).toList();
	}

}
