package com.gigwork.core.reference.persistence;

import java.util.List;

import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.CityInfo;
import com.gigwork.core.reference.LanguageInfo;
import com.gigwork.core.reference.LocalityInfo;
import com.gigwork.core.reference.SpecialisationInfo;
import com.gigwork.core.reference.ZoneInfo;

/**
 * Everything the snapshot needs, already as the module's API records. Categories and specialisations include
 * retired ones (old gigs and profiles keep pointing at them); the other lists hold only what is live: live
 * languages, live cities, their live zones and live localities.
 *
 * @param categories carry only their live specialisations
 * @param specialisations all of them
 */
public record ReferenceRows(List<CategoryInfo> categories, List<SpecialisationInfo> specialisations,
		List<LanguageInfo> languages, List<CityInfo> cities, List<ZoneInfo> zones, List<LocalityInfo> localities) {
}
