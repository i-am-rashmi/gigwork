package com.gigwork.core.reference.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.CityInfo;
import com.gigwork.core.reference.LanguageInfo;
import com.gigwork.core.reference.LocalityInfo;
import com.gigwork.core.reference.SpecialisationInfo;
import com.gigwork.core.reference.ZoneInfo;

/** Reads the whole reference schema once (it has a few thousand rows) to build the in-memory snapshot. */
@Repository
public class ReferenceReader {

	private final CityRepository cities;
	private final ZoneRepository zones;
	private final CategoryRepository categories;
	private final SpecialisationRepository specialisations;
	private final LanguageRepository languages;
	private final LocalityRepository localities;

	ReferenceReader(CityRepository cities, ZoneRepository zones, CategoryRepository categories,
			SpecialisationRepository specialisations, LanguageRepository languages, LocalityRepository localities) {
		this.cities = cities;
		this.zones = zones;
		this.categories = categories;
		this.specialisations = specialisations;
		this.languages = languages;
		this.localities = localities;
	}

	@Transactional(readOnly = true)
	public ReferenceRows readAll() {
		List<SpecialisationInfo> allSpecialisations = specialisations.findAll().stream()
				.map(s -> new SpecialisationInfo(s.getId(), s.getCategoryId(), s.getSlug(), s.getName(), s.isLive(),
						s.getDisplayOrder()))
				.sorted(Comparator.comparingInt(SpecialisationInfo::displayOrder).thenComparing(SpecialisationInfo::slug))
				.toList();

		List<CategoryInfo> allCategories = categories.findAll().stream()
				.map(c -> new CategoryInfo(c.getId(), c.getSlug(), c.getName(), c.getWorkMode(), c.isLanguageRelevant(),
						c.isLive(), c.getDisplayOrder(),
						allSpecialisations.stream().filter(s -> s.categoryId().equals(c.getId()) && s.live()).toList()))
				.sorted(Comparator.comparingInt(CategoryInfo::displayOrder).thenComparing(CategoryInfo::slug))
				.toList();

		List<LanguageInfo> liveLanguages = languages.findAll().stream().filter(Language::isLive)
				.map(l -> new LanguageInfo(l.getCode(), l.getName(), l.getDisplayOrder()))
				.sorted(Comparator.comparingInt(LanguageInfo::displayOrder).thenComparing(LanguageInfo::code)).toList();

		List<City> liveCityRows = cities.findAll().stream().filter(City::isLive).toList();
		Set<UUID> liveCityIds = liveCityRows.stream().map(City::getId).collect(Collectors.toSet());
		List<CityInfo> liveCities = liveCityRows.stream().map(c -> new CityInfo(c.getId(), c.getSlug(), c.getName()))
				.sorted(Comparator.comparing(CityInfo::name).thenComparing(CityInfo::slug)).toList();

		List<ZoneInfo> liveZones = zones.findAll().stream().filter(z -> z.isLive() && liveCityIds.contains(z.getCityId()))
				.map(z -> new ZoneInfo(z.getId(), z.getCityId(), z.getName()))
				.sorted(Comparator.comparing(ZoneInfo::name).thenComparing(ZoneInfo::id)).toList();

		List<LocalityInfo> liveLocalities = localities.findAll().stream()
				.filter(l -> l.isLive() && liveCityIds.contains(l.getCityId()))
				.map(l -> new LocalityInfo(l.getId(), l.getCityId(), l.getZoneId(), l.getName(), l.getPin(), l.getLat(),
						l.getLng()))
				.sorted(Comparator.comparing(LocalityInfo::name).thenComparing(LocalityInfo::id)).toList();

		return new ReferenceRows(allCategories, allSpecialisations, liveLanguages, liveCities, liveZones, liveLocalities);
	}

}
