package com.gigwork.core.reference.web.dto;

import java.util.List;
import java.util.UUID;

import com.gigwork.core.reference.CategoryInfo;

/**
 * @param workMode ON_SITE, REMOTE or BOTH
 * @param languageRelevant whether a gig or Talent in this category may name a language
 * @param specialisations the live specialisations, in display order
 */
public record CategoryResponse(UUID id, String slug, String name, String workMode, boolean languageRelevant,
		List<SpecialisationResponse> specialisations) {

	public static CategoryResponse of(CategoryInfo c) {
		return new CategoryResponse(c.id(), c.slug(), c.name(), c.workMode(), c.languageRelevant(),
				c.specialisations().stream().map(SpecialisationResponse::of).toList());
	}

}
