package com.gigwork.core.reference;

import java.util.List;
import java.util.UUID;

/**
 * A category with its LIVE specialisations, in display order.
 *
 * @param workMode ON_SITE, REMOTE or BOTH: the work modes a gig in this category may use
 * @param languageRelevant whether a gig or Talent in this category may name a language
 * @param live false for a retired category, which stays readable for old gigs and profiles
 */
public record CategoryInfo(UUID id, String slug, String name, String workMode, boolean languageRelevant, boolean live,
		int displayOrder, List<SpecialisationInfo> specialisations) {
}
