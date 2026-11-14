package com.gigwork.core.reference.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.CityInfo;
import com.gigwork.core.reference.LanguageInfo;
import com.gigwork.core.reference.LocalityInfo;
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
	private final Map<UUID, CityInfo> citiesById;
	private final Map<UUID, ZoneInfo> zonesById;
	private final Map<UUID, LocalityInfo> localitiesById;
	private final List<LocalityInfo> localityList;

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
		this.citiesById = rows.cities().stream().collect(Collectors.toUnmodifiableMap(CityInfo::id, c -> c));
		this.zonesById = rows.zones().stream().collect(Collectors.toUnmodifiableMap(ZoneInfo::id, z -> z));
		this.localitiesById = rows.localities().stream().collect(Collectors.toUnmodifiableMap(LocalityInfo::id, l -> l));
		this.localityList = List.copyOf(rows.localities());
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

	CityInfo city(UUID id) {
		return citiesById.get(id);
	}

	ZoneInfo zone(UUID id) {
		return zonesById.get(id);
	}

	LocalityInfo locality(UUID id) {
		return localitiesById.get(id);
	}

	/** The nearest locality centre (great-circle distance), if within {@code maxMeters}; ties go to the lowest id. */
	LocalityInfo nearestLocality(double lat, double lng, double maxMeters) {
		LocalityInfo best = null;
		double bestMeters = Double.MAX_VALUE;
		for (LocalityInfo l : localityList) {
			double meters = haversineMeters(lat, lng, l.lat(), l.lng());
			if (meters < bestMeters || (meters == bestMeters && best != null && l.id().compareTo(best.id()) < 0)) {
				best = l;
				bestMeters = meters;
			}
		}
		return best != null && bestMeters <= maxMeters ? best : null;
	}

	static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
		double p = Math.PI / 180;
		double a = Math.pow(Math.sin((lat2 - lat1) * p / 2), 2)
				+ Math.cos(lat1 * p) * Math.cos(lat2 * p) * Math.pow(Math.sin((lng2 - lng1) * p / 2), 2);
		return 2 * 6_371_000 * Math.asin(Math.sqrt(a));
	}

	LocalitySearch localities() {
		return localities;
	}

}
