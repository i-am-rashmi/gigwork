package com.gigwork.core.reference.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.CityInfo;
import com.gigwork.core.reference.LanguageInfo;
import com.gigwork.core.reference.SpecialisationInfo;
import com.gigwork.core.reference.ZoneInfo;
import com.gigwork.core.reference.persistence.ReferenceRows;

/** An immutable copy of the reference data. It is replaced whole, never changed. */
final class ReferenceSnapshot {

	private final List<CategoryInfo> liveCategories;
	private final Map<UUID, CategoryInfo> categories;
	private final Map<UUID, SpecialisationInfo> specialisations;
	private final List<LanguageInfo> languages;
	private final List<CityInfo> cities;
	private final Map<UUID, List<ZoneInfo>> zonesByCity;
	private final LocalitySearch localities;

	ReferenceSnapshot(ReferenceRows rows) {
		this.categories = rows.categories().stream().collect(Collectors.toUnmodifiableMap(CategoryInfo::id, c -> c));
		this.liveCategories = rows.categories().stream().filter(CategoryInfo::live).toList();
		this.specialisations = rows.specialisations().stream()
				.collect(Collectors.toUnmodifiableMap(SpecialisationInfo::id, s -> s));
		this.languages = List.copyOf(rows.languages());
		this.cities = List.copyOf(rows.cities());
		Map<UUID, List<ZoneInfo>> zones = new HashMap<>();
		rows.zones().forEach(z -> zones.computeIfAbsent(z.cityId(), k -> new java.util.ArrayList<>()).add(z));
		zones.replaceAll((k, v) -> List.copyOf(v));
		this.zonesByCity = Map.copyOf(zones);
		this.localities = new LocalitySearch(rows.localities());
	}

	/** What a working installation always has; an empty snapshot means the seed data did not load. */
	String problem() {
		if (liveCategories.isEmpty()) {
			return "no live categories";
		}
		if (languages.isEmpty()) {
			return "no live languages";
		}
		if (cities.isEmpty()) {
			return "no live cities";
		}
		if (zonesByCity.isEmpty()) {
			return "no live zones";
		}
		if (localities.size() == 0) {
			return "no live localities";
		}
		return null;
	}

	List<CategoryInfo> liveCategories() {
		return liveCategories;
	}

	CategoryInfo category(UUID id) {
		return categories.get(id);
	}

	SpecialisationInfo specialisation(UUID id) {
		return specialisations.get(id);
	}

	List<LanguageInfo> languages() {
		return languages;
	}

	List<CityInfo> cities() {
		return cities;
	}

	List<ZoneInfo> zones(UUID cityId) {
		return zonesByCity.getOrDefault(cityId, List.of());
	}

	LocalitySearch localities() {
		return localities;
	}

}
