package com.gigwork.core.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.gigwork.core.reference.support.ReferenceIntegrationTest;
import com.gigwork.core.reference.support.SeedFixtures;

/** {@link ReferenceData} over the real seed data and PostGIS. */
@ReferenceIntegrationTest
class ReferenceDataIntegrationTest {

	@Autowired
	ReferenceData reference;
	@Autowired
	JdbcClient jdbc;

	private String zoneName(UUID zoneId) {
		return jdbc.sql("SELECT name FROM reference.zone WHERE id = :id").param("id", zoneId).query(String.class).single();
	}

	// ---- snapshot ------------------------------------------------------------------------------------------

	@Test
	void categoriesComeInDisplayOrderWithTheirSpecialisations() {
		List<CategoryInfo> categories = reference.liveCategories();

		assertThat(categories).hasSize(SeedFixtures.rows("categories.csv"));
		assertThat(categories).extracting(CategoryInfo::displayOrder).isSorted();
		assertThat(categories.get(0).slug()).isEqualTo("photographer");
		assertThat(categories).allSatisfy(c -> assertThat(c.specialisations()).isNotEmpty());
		assertThat(categories.stream().mapToInt(c -> c.specialisations().size()).sum())
				.isEqualTo(SeedFixtures.rows("specialisations.csv"));
		assertThat(categories).filteredOn(CategoryInfo::languageRelevant).extracting(CategoryInfo::slug)
				.contains("anchor", "singer", "content-writer").doesNotContain("photographer");
	}

	@Test
	void lookupsByIdAndLanguageRelevance() {
		CategoryInfo anchor = reference.liveCategories().stream().filter(c -> c.slug().equals("anchor")).findFirst().orElseThrow();
		CategoryInfo photographer = reference.liveCategories().get(0);

		assertThat(reference.isLiveCategory(anchor.id())).isTrue();
		assertThat(reference.isLanguageRelevant(anchor.id())).isTrue();
		assertThat(reference.isLanguageRelevant(photographer.id())).isFalse();
		assertThat(reference.specialisation(photographer.specialisations().get(0).id()))
				.hasValueSatisfying(s -> assertThat(s.categoryId()).isEqualTo(photographer.id()));
		assertThat(reference.isLiveCategory(UUID.randomUUID())).isFalse();
	}

	@Test
	void languagesCitiesAndZones() {
		assertThat(reference.languages()).hasSize(SeedFixtures.rows("languages.csv"));
		assertThat(reference.languages().get(0).code()).isEqualTo("en");
		CityInfo bengaluru = reference.liveCities().get(0);
		assertThat(bengaluru.slug()).isEqualTo("bengaluru");
		assertThat(reference.zones(bengaluru.id())).extracting(ZoneInfo::name)
				.containsExactly("Central", "East", "North", "South", "West");
	}

	// ---- search --------------------------------------------------------------------------------------------

	@Test
	void duplicateNamesAreAllReturned() {
		List<LocalityInfo> found = reference.searchLocalities("maruthi nagara", 20);

		assertThat(found).extracting(LocalityInfo::name).containsOnly("Maruthi Nagara");
		assertThat(found).hasSizeGreaterThanOrEqualTo(2);
		assertThat(found).extracting(LocalityInfo::id).doesNotHaveDuplicates();
	}

	@Test
	void nameAndPinPrefixSearchOverTheRealData() {
		assertThat(reference.searchLocalities("INDIRANAGAR", 10)).extracting(LocalityInfo::name).contains("Indiranagar");
		assertThat(reference.searchLocalities("5600", 10)).hasSize(10)
				.allSatisfy(l -> assertThat(l.pin()).startsWith("5600"));
		assertThat(reference.searchLocalities("indira", 3)).hasSizeLessThanOrEqualTo(3);
	}

	// ---- resolveArea ---------------------------------------------------------------------------------------

	@Test
	void aPointInsideAZoneResolvesToIt() {
		AreaResolution mgRoad = reference.resolveArea(12.9756, 77.6097);

		assertThat(mgRoad.insideZone()).isTrue();
		assertThat(mgRoad.cityId()).isEqualTo(reference.liveCities().get(0).id());
		assertThat(zoneName(mgRoad.zoneId())).isEqualTo("Central");
		assertThat(reference.resolveArea(12.9756, 77.6097)).isEqualTo(mgRoad);
	}

	@Test
	void aPointOnASharedBorderResolvesDeterministicallyToTheLowestZoneId() {
		// find a point that two zones both cover: a vertex of their common boundary (exact in both polygons)
		var border = jdbc.sql("""
				SELECT ST_Y(d.geom) AS lat, ST_X(d.geom) AS lng
				FROM reference.zone a JOIN reference.zone b ON a.city_id = b.city_id AND a.id < b.id,
				     LATERAL ST_DumpPoints(ST_CollectionExtract(ST_Intersection(a.boundary, b.boundary), 2)) d
				WHERE ST_Covers(a.boundary, d.geom) AND ST_Covers(b.boundary, d.geom)
				ORDER BY a.id, b.id, d.path
				LIMIT 1""").query((rs, n) -> new double[] { rs.getDouble("lat"), rs.getDouble("lng") }).optional();

		assertThat(border).as("two zones share a border point that both cover").isPresent();
		double[] point = border.get();
		var covering = jdbc.sql("""
				SELECT id FROM reference.zone WHERE ST_Covers(boundary, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)) ORDER BY id""")
				.param("lng", point[1]).param("lat", point[0]).query(UUID.class).list();
		assertThat(covering).as("zones covering the border point").hasSizeGreaterThanOrEqualTo(2);

		for (int i = 0; i < 5; i++) {
			AreaResolution resolved = reference.resolveArea(point[0], point[1]);
			assertThat(resolved.insideZone()).isTrue();
			assertThat(resolved.zoneId()).isEqualTo(covering.get(0));
		}
	}

	@Test
	void aPointOutsideEveryZoneGetsTheNearestOne() {
		AreaResolution north = reference.resolveArea(13.35, 77.57); // far north of the city
		AreaResolution south = reference.resolveArea(12.60, 77.58); // far south

		assertThat(north.insideZone()).isFalse();
		assertThat(zoneName(north.zoneId())).isEqualTo("North");
		assertThat(south.insideZone()).isFalse();
		assertThat(zoneName(south.zoneId())).isEqualTo("South");
		assertThat(north.cityId()).isEqualTo(reference.liveCities().get(0).id());
	}

	@Test
	void aPointOnTheOtherSideOfTheCountryStillGetsTheNearestZone() {
		AreaResolution delhi = reference.resolveArea(28.61, 77.21);

		assertThat(delhi.insideZone()).isFalse();
		assertThat(delhi.zoneId()).isNotNull();
	}

	@Test
	void distanceToTheNearestZoneIsZeroInsideAndInMetresOutside() {
		assertThat(reference.resolveArea(12.9756, 77.6097).distanceToZoneMeters()).isZero();

		double north = reference.resolveArea(13.35, 77.57).distanceToZoneMeters(); // about 23 km above the city's edge
		assertThat(north).isBetween(20_000.0, 27_000.0);
		assertThat(reference.resolveArea(28.61, 77.21).distanceToZoneMeters()).isGreaterThan(1_500_000); // Delhi
	}

	@Test
	void lookupsByIdForCitiesZonesAndLocalities() {
		CityInfo city = reference.liveCities().get(0);
		ZoneInfo zone = reference.zones(city.id()).get(0);
		LocalityInfo locality = reference.searchLocalities("indiranagar", 1).get(0);

		assertThat(reference.city(city.id())).contains(city);
		assertThat(reference.zone(zone.id())).contains(zone);
		assertThat(reference.locality(locality.id())).contains(locality);
		assertThat(reference.city(UUID.randomUUID())).isEmpty();
		assertThat(reference.zone(UUID.randomUUID())).isEmpty();
		assertThat(reference.locality(UUID.randomUUID())).isEmpty();
	}

	@Test
	void nearestLocalityIsFoundWithinTheDistanceOnly() {
		LocalityInfo indiranagar = reference.searchLocalities("indiranagar", 1).get(0);

		assertThat(reference.nearestLocality(indiranagar.lat(), indiranagar.lng(), 50)).hasValueSatisfying(l -> assertThat(l.id()).isEqualTo(indiranagar.id()));
		assertThat(reference.nearestLocality(indiranagar.lat() + 0.003, indiranagar.lng(), 2_000)).isPresent();
		assertThat(reference.nearestLocality(28.61, 77.21, 2_000)).isEmpty(); // Delhi: nothing within 2 km
		assertThatThrownBy(() -> reference.nearestLocality(95, 0, 2_000)).isInstanceOf(InvalidCoordinatesException.class);
	}

	@Test
	void badCoordinatesAreRefused() {
		assertThatThrownBy(() -> reference.resolveArea(95, 77)).isInstanceOf(InvalidCoordinatesException.class);
	}

}
