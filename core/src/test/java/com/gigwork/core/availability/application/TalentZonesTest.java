package com.gigwork.core.availability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gigwork.core.availability.config.AvailabilityProperties;
import com.gigwork.core.profile.TalentProfileSummary;
import com.gigwork.core.profile.TalentProfiles;
import com.gigwork.core.reference.CityInfo;
import com.gigwork.core.reference.ReferenceData;

/** Which zone a Talent's dates are read in. Stubs stand in for the Profile and Reference modules. */
class TalentZonesTest {

	private static final UUID ACCOUNT = UUID.randomUUID();
	private static final UUID CITY = UUID.randomUUID();

	private final TalentProfiles profiles = mock(TalentProfiles.class);
	private final ReferenceData reference = mock(ReferenceData.class);
	private final TalentZones zones = new TalentZones(profiles, reference, new AvailabilityProperties("Asia/Kolkata", 200, 90, 92));

	@Test
	void theTalentsCityZoneWins() {
		when(profiles.summary(ACCOUNT)).thenReturn(Optional.of(new TalentProfileSummary(ACCOUNT, "ACTIVE", CITY)));
		when(reference.city(CITY)).thenReturn(Optional.of(new CityInfo(CITY, "new-york", "New York", "America/New_York")));

		assertThat(zones.of(ACCOUNT)).isEqualTo(new TalentZones.TalentZone(true, ZoneId.of("America/New_York")));
	}

	@Test
	void noSavedLocationMeansTheDefaultZoneButStillAProfile() {
		when(profiles.summary(ACCOUNT)).thenReturn(Optional.of(new TalentProfileSummary(ACCOUNT, "INCOMPLETE", null)));

		assertThat(zones.of(ACCOUNT)).isEqualTo(new TalentZones.TalentZone(true, ZoneId.of("Asia/Kolkata")));
	}

	@Test
	void aCityThatIsNoLongerLiveFallsBackToTheDefault() {
		when(profiles.summary(ACCOUNT)).thenReturn(Optional.of(new TalentProfileSummary(ACCOUNT, "ACTIVE", CITY)));
		when(reference.city(CITY)).thenReturn(Optional.empty());

		assertThat(zones.of(ACCOUNT).zone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
	}

	@Test
	void noProfileMeansNoProfile() {
		when(profiles.summary(ACCOUNT)).thenReturn(Optional.empty());

		assertThat(zones.of(ACCOUNT)).isEqualTo(new TalentZones.TalentZone(false, ZoneId.of("Asia/Kolkata")));
	}

	@Test
	void aBadDefaultZoneStopsTheStartInsteadOfFailingLater() {
		assertThatThrownBy(() -> new TalentZones(profiles, reference, new AvailabilityProperties("Mars/Olympus", 200, 90, 92)))
				.isInstanceOf(TalentZones.IllegalConfigurationException.class);
		assertThat(List.of("Asia/Kolkata", "UTC").stream().map(ZoneId::of)).hasSize(2);
	}

}
