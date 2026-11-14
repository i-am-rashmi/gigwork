package com.gigwork.core.reference;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask Reference Data (data model §4). Categories, specialisations, languages, cities and
 * localities come from an immutable in-memory snapshot loaded at startup, so these calls never touch the database;
 * only {@link #resolveArea} does. Everything is read-only: reference data changes only through migrations.
 */
public interface ReferenceData {

	/** LIVE categories in display order, each with its LIVE specialisations. */
	List<CategoryInfo> liveCategories();

	/** Any category, live or retired (old gigs and profiles keep referring to retired ones); its specialisations are the LIVE ones. */
	Optional<CategoryInfo> category(UUID id);

	boolean isLiveCategory(UUID id);

	/** Any specialisation, live or retired. */
	Optional<SpecialisationInfo> specialisation(UUID id);

	/** LIVE languages in display order. */
	List<LanguageInfo> languages();

	/** Whether the category lets gigs and Talent name a language; false for an unknown category. */
	boolean isLanguageRelevant(UUID categoryId);

	/** LIVE cities in name order (FR-34: only live cities are offered). */
	List<CityInfo> liveCities();

	/** LIVE zones of a LIVE city by name; empty for an unknown or not-live city. */
	List<ZoneInfo> zones(UUID cityId);

	/** A LIVE city (of a live city), by id. */
	Optional<CityInfo> city(UUID id);

	/** A LIVE zone of a live city, by id. */
	Optional<ZoneInfo> zone(UUID id);

	/** A LIVE locality of a live city, by id. */
	Optional<LocalityInfo> locality(UUID id);

	/** The live locality whose centre is nearest to the point, if it is within {@code maxMeters}. */
	Optional<LocalityInfo> nearestLocality(double lat, double lng, double maxMeters);

	/**
	 * The city and zone of a point: the zone covering it (lowest zone id on a shared border), else the nearest zone
	 * of a live city.
	 *
	 * @throws InvalidCoordinatesException for a latitude or longitude out of range
	 * @throws NoZonesDefinedException if no live city has a zone
	 */
	AreaResolution resolveArea(double lat, double lng);

	/**
	 * Localities of live cities matching what a user typed: digits match the start of a PIN; anything else must
	 * match the start of words of the name, ignoring case and accents. At most {@code limit} results (none for a
	 * limit of 0 or less).
	 */
	List<LocalityInfo> searchLocalities(String text, int limit);

}
