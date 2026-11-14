package com.gigwork.core.reference.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.CityInfo;
import com.gigwork.core.reference.InvalidCoordinatesException;
import com.gigwork.core.reference.LanguageInfo;
import com.gigwork.core.reference.LocalityInfo;
import com.gigwork.core.reference.SpecialisationInfo;
import com.gigwork.core.reference.ZoneInfo;
import com.gigwork.core.reference.persistence.AreaRepository;
import com.gigwork.core.reference.persistence.ReferenceReader;
import com.gigwork.core.reference.persistence.ReferenceRows;

class ReferenceDataServiceTest {

	private static final UUID CITY = UUID.randomUUID();
	private static final UUID ZONE = UUID.randomUUID();
	private static final UUID LIVE_CATEGORY = UUID.randomUUID();
	private static final UUID RETIRED_CATEGORY = UUID.randomUUID();
	private static final UUID LIVE_SPEC = UUID.randomUUID();
	private static final UUID RETIRED_SPEC = UUID.randomUUID();

	private static final SpecialisationInfo LIVE_SPECIALISATION = new SpecialisationInfo(LIVE_SPEC, LIVE_CATEGORY, "wedding", "Wedding", true, 1);
	private static final SpecialisationInfo RETIRED_SPECIALISATION = new SpecialisationInfo(RETIRED_SPEC, LIVE_CATEGORY, "old", "Old", false, 2);

	private static ReferenceRows rows() {
		return new ReferenceRows(
				List.of(new CategoryInfo(LIVE_CATEGORY, "anchor", "Anchor", "ON_SITE", true, true, 1, List.of(LIVE_SPECIALISATION)),
						new CategoryInfo(RETIRED_CATEGORY, "gone", "Gone", "BOTH", false, false, 2, List.of())),
				List.of(LIVE_SPECIALISATION, RETIRED_SPECIALISATION), List.of(new LanguageInfo("kn", "Kannada", 1)),
				List.of(new CityInfo(CITY, "bengaluru", "Bengaluru", "Asia/Kolkata")), List.of(new ZoneInfo(ZONE, CITY, "East")),
				List.of(new LocalityInfo(UUID.randomUUID(), CITY, ZONE, "Indiranagar", "560038", 12.97, 77.64)));
	}

	private static ReferenceDataService service(ReferenceRows rows) {
		ReferenceReader reader = mock(ReferenceReader.class);
		when(reader.readAll()).thenReturn(rows);
		return new ReferenceDataService(reader, mock(AreaRepository.class));
	}

	@Test
	void liveListsExcludeRetiredRowsButLookupsStillFindThem() {
		ReferenceDataService service = service(rows());

		assertThat(service.liveCategories()).extracting(CategoryInfo::slug).containsExactly("anchor");
		assertThat(service.liveCategories().get(0).specialisations()).containsExactly(LIVE_SPECIALISATION);
		assertThat(service.isLiveCategory(LIVE_CATEGORY)).isTrue();
		assertThat(service.isLiveCategory(RETIRED_CATEGORY)).isFalse();
		assertThat(service.category(RETIRED_CATEGORY)).isPresent();
		assertThat(service.specialisation(RETIRED_SPEC)).hasValueSatisfying(s -> assertThat(s.live()).isFalse());
		assertThat(service.specialisation(UUID.randomUUID())).isEmpty();
	}

	@Test
	void languageRelevanceComesFromTheCategoryAndIsFalseForAnUnknownOne() {
		ReferenceDataService service = service(rows());

		assertThat(service.isLanguageRelevant(LIVE_CATEGORY)).isTrue();
		assertThat(service.isLanguageRelevant(RETIRED_CATEGORY)).isFalse();
		assertThat(service.isLanguageRelevant(UUID.randomUUID())).isFalse();
	}

	@Test
	void zonesOfAnUnknownCityAreEmpty() {
		ReferenceDataService service = service(rows());

		assertThat(service.zones(CITY)).hasSize(1);
		assertThat(service.zones(UUID.randomUUID())).isEmpty();
	}

	@Test
	void startupFailsWhenTheSnapshotIsEmpty() {
		ReferenceRows empty = new ReferenceRows(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

		assertThatThrownBy(() -> service(empty)).isInstanceOf(ReferenceDataUnavailableException.class)
				.hasMessageContaining("no live categories");
	}

	@Test
	void startupFailsWhenAnyPartIsMissing() {
		ReferenceRows base = rows();
		assertThatThrownBy(() -> service(new ReferenceRows(base.categories(), base.specialisations(), List.of(), base.cities(), base.zones(), base.localities())))
				.hasMessageContaining("no live languages");
		assertThatThrownBy(() -> service(new ReferenceRows(base.categories(), base.specialisations(), base.languages(), List.of(), List.of(), base.localities())))
				.hasMessageContaining("no live cities");
		assertThatThrownBy(() -> service(new ReferenceRows(base.categories(), base.specialisations(), base.languages(), base.cities(), base.zones(), List.of())))
				.hasMessageContaining("no live localities");
	}

	@Test
	void resolveAreaRefusesCoordinatesOutOfRange() {
		ReferenceDataService service = service(rows());

		for (double[] bad : new double[][] { { 91, 77 }, { -91, 77 }, { 12, 181 }, { 12, -181 }, { Double.NaN, 77 }, { 12, Double.NaN } }) {
			assertThatThrownBy(() -> service.resolveArea(bad[0], bad[1])).isInstanceOf(InvalidCoordinatesException.class);
		}
	}

}
