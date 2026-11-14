package com.gigwork.core.reference.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.gigwork.core.reference.AreaResolution;
import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.CityInfo;
import com.gigwork.core.reference.InvalidCoordinatesException;
import com.gigwork.core.reference.LanguageInfo;
import com.gigwork.core.reference.LocalityInfo;
import com.gigwork.core.reference.NoZonesDefinedException;
import com.gigwork.core.reference.ReferenceData;
import com.gigwork.core.reference.SpecialisationInfo;
import com.gigwork.core.reference.ZoneInfo;
import com.gigwork.core.reference.persistence.AreaRepository;
import com.gigwork.core.reference.persistence.ReferenceReader;

/**
 * Serves reference data from an immutable snapshot loaded once at startup (it fails the start if the snapshot is
 * empty). The snapshot sits in an {@link AtomicReference} and would be replaced whole on a reload; none is needed
 * yet. Only {@link #resolveArea} queries the database.
 */
@Service
class ReferenceDataService implements ReferenceData {

	private static final Logger log = LoggerFactory.getLogger(ReferenceDataService.class);

	private final AtomicReference<ReferenceSnapshot> snapshot = new AtomicReference<>();
	private final ReferenceReader reader;
	private final AreaRepository areas;

	ReferenceDataService(ReferenceReader reader, AreaRepository areas) {
		this.reader = reader;
		this.areas = areas;
		reload();
	}

	/** Builds a new snapshot and swaps it in; the old one stays in use if the new one is unusable. */
	final void reload() {
		ReferenceSnapshot fresh = new ReferenceSnapshot(reader.readAll());
		String problem = fresh.problem();
		if (problem != null) {
			throw new ReferenceDataUnavailableException(problem);
		}
		snapshot.set(fresh);
		log.info("Reference snapshot loaded: {} live categories, {} languages, {} cities, {} localities",
				fresh.liveCategories().size(), fresh.languages().size(), fresh.cities().size(), fresh.localities().size());
	}

	@Override
	public List<CategoryInfo> liveCategories() {
		return snapshot.get().liveCategories();
	}

	@Override
	public Optional<CategoryInfo> category(UUID id) {
		return Optional.ofNullable(snapshot.get().category(id));
	}

	@Override
	public boolean isLiveCategory(UUID id) {
		CategoryInfo category = snapshot.get().category(id);
		return category != null && category.live();
	}

	@Override
	public Optional<SpecialisationInfo> specialisation(UUID id) {
		return Optional.ofNullable(snapshot.get().specialisation(id));
	}

	@Override
	public List<LanguageInfo> languages() {
		return snapshot.get().languages();
	}

	@Override
	public boolean isLanguageRelevant(UUID categoryId) {
		CategoryInfo category = snapshot.get().category(categoryId);
		return category != null && category.languageRelevant();
	}

	@Override
	public List<CityInfo> liveCities() {
		return snapshot.get().cities();
	}

	@Override
	public List<ZoneInfo> zones(UUID cityId) {
		return snapshot.get().zones(cityId);
	}

	@Override
	public Optional<CityInfo> city(UUID id) {
		return Optional.ofNullable(snapshot.get().city(id));
	}

	@Override
	public Optional<ZoneInfo> zone(UUID id) {
		return Optional.ofNullable(snapshot.get().zone(id));
	}

	@Override
	public Optional<LocalityInfo> locality(UUID id) {
		return Optional.ofNullable(snapshot.get().locality(id));
	}

	@Override
	public Optional<LocalityInfo> nearestLocality(double lat, double lng, double maxMeters) {
		if (!(lat >= -90 && lat <= 90) || !(lng >= -180 && lng <= 180)) {
			throw new InvalidCoordinatesException(lat, lng);
		}
		return Optional.ofNullable(snapshot.get().nearestLocality(lat, lng, maxMeters));
	}

	@Override
	public AreaResolution resolveArea(double lat, double lng) {
		if (!(lat >= -90 && lat <= 90) || !(lng >= -180 && lng <= 180)) {
			throw new InvalidCoordinatesException(lat, lng);
		}
		return areas.findCovering(lat, lng).or(() -> areas.findNearest(lat, lng))
				.orElseThrow(NoZonesDefinedException::new);
	}

	@Override
	public List<LocalityInfo> searchLocalities(String text, int limit) {
		return snapshot.get().localities().search(text, limit);
	}

}
