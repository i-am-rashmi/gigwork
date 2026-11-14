package com.gigwork.core.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocationResolverTest {

	private static final UUID CITY = UUID.randomUUID();
	private static final UUID ZONE = UUID.randomUUID();

	private final ReferenceData reference = mock(ReferenceData.class);
	private final LocationResolver resolver = new LocationResolver(reference, 50, 2000);

	@BeforeEach
	void setUp() {
		when(reference.zone(ZONE)).thenReturn(Optional.of(new ZoneInfo(ZONE, CITY, "Central")));
	}

	private static LocalityInfo locality(UUID id, String name, double lat, double lng) {
		return new LocalityInfo(id, CITY, ZONE, name, null, lat, lng);
	}

	@Test
	void aLocalityIsUsedAtItsOwnCentreWithoutRounding() {
		UUID id = UUID.randomUUID();
		when(reference.locality(id)).thenReturn(Optional.of(locality(id, "Indiranagar", 12.97194, 77.64105)));
		when(reference.resolveArea(12.97194, 77.64105)).thenReturn(new AreaResolution(CITY, ZONE, true, 0));

		ResolvedLocation place = resolver.resolve(id, null, null);

		assertThat(place).isEqualTo(new ResolvedLocation(12.97194, 77.64105, CITY, ZONE, "Indiranagar", id));
	}

	@Test
	void aLocalityThatIsNotOfferedIsRefused() {
		when(reference.locality(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> resolver.resolve(UUID.randomUUID(), null, null))
				.isInstanceOf(InvalidLocationException.class).hasMessageContaining("locality");
	}

	@Test
	void aMapPointIsRoundedToThreeDecimalsHalfUpAndLabelledWithTheNearestLocality() {
		when(reference.resolveArea(12.976, 77.605)).thenReturn(new AreaResolution(CITY, ZONE, true, 0));
		UUID near = UUID.randomUUID();
		when(reference.nearestLocality(12.976, 77.605, 2000)).thenReturn(Optional.of(locality(near, "MG Road", 12.9756, 77.6097)));

		ResolvedLocation place = resolver.resolve(null, 12.97649, 77.60451);

		assertThat(place).isEqualTo(new ResolvedLocation(12.976, 77.605, CITY, ZONE, "MG Road", null));
	}

	@Test
	void aMapPointWithNoLocalityNearbyIsLabelledWithItsZone() {
		when(reference.resolveArea(12.5, 77.5)).thenReturn(new AreaResolution(CITY, ZONE, true, 0));
		when(reference.nearestLocality(12.5, 77.5, 2000)).thenReturn(Optional.empty());

		assertThat(resolver.resolve(null, 12.5, 77.5).label()).isEqualTo("Central");
	}

	@Test
	void aMapPointInAnUnknownZoneIsLabelledUnknownArea() {
		UUID gone = UUID.randomUUID();
		when(reference.resolveArea(12.5, 77.5)).thenReturn(new AreaResolution(CITY, gone, true, 0));
		when(reference.nearestLocality(12.5, 77.5, 2000)).thenReturn(Optional.empty());
		when(reference.zone(gone)).thenReturn(Optional.empty());

		assertThat(resolver.resolve(null, 12.5, 77.5).label()).isEqualTo("Unknown area");
	}

	@Test
	void aMapPointFartherThanTheLimitFromTheNearestZoneIsRefused() {
		when(reference.resolveArea(13.35, 77.57)).thenReturn(new AreaResolution(CITY, ZONE, false, 50_001));

		assertThatThrownBy(() -> resolver.resolve(null, 13.35, 77.57))
				.isInstanceOf(InvalidLocationException.class).hasMessageContaining("more than 50 km");
	}

	@Test
	void aMapPointExactlyAtTheLimitIsAccepted() {
		when(reference.resolveArea(13.35, 77.57)).thenReturn(new AreaResolution(CITY, ZONE, false, 50_000));
		when(reference.nearestLocality(13.35, 77.57, 2000)).thenReturn(Optional.empty());

		assertThat(resolver.resolve(null, 13.35, 77.57).zoneId()).isEqualTo(ZONE);
	}

	@Test
	void theLimitsAreTheOnesGiven() {
		LocationResolver strict = new LocationResolver(reference, 2.5, 100);
		when(reference.resolveArea(13.0, 77.0)).thenReturn(new AreaResolution(CITY, ZONE, false, 2_501));

		assertThatThrownBy(() -> strict.resolve(null, 13.0, 77.0)).hasMessageContaining("more than 2.5 km");
	}

	@Test
	void exactlyOneOfLocalityAndPointIsRequired() {
		assertThatThrownBy(() -> resolver.resolve(null, null, null)).isInstanceOf(InvalidLocationException.class)
				.hasMessageContaining("either a locality or a map point");
		assertThatThrownBy(() -> resolver.resolve(UUID.randomUUID(), 12.9, 77.6))
				.isInstanceOf(InvalidLocationException.class).hasMessageContaining("either a locality or a map point");
	}

	@Test
	void aMapPointNeedsBothCoordinates() {
		assertThatThrownBy(() -> resolver.resolve(null, 12.9, null)).isInstanceOf(InvalidLocationException.class)
				.hasMessageContaining("both a latitude and a longitude");
		assertThatThrownBy(() -> resolver.resolve(null, null, 77.6)).isInstanceOf(InvalidLocationException.class);
	}

	@Test
	void coordinatesOffTheEarthAreRefused() {
		when(reference.resolveArea(anyDouble(), anyDouble())).thenThrow(new InvalidCoordinatesException(95, 0));

		assertThatThrownBy(() -> resolver.resolve(null, 95.0, 0.0)).isInstanceOf(InvalidLocationException.class)
				.hasMessageContaining("not a valid place");
	}

	@Test
	void whenNoAreaIsServedYetThePointIsRefused() {
		when(reference.resolveArea(anyDouble(), anyDouble())).thenThrow(new NoZonesDefinedException());

		assertThatThrownBy(() -> resolver.resolve(null, 12.9, 77.6)).isInstanceOf(InvalidLocationException.class)
				.hasMessageContaining("don't serve any area");
	}

	@Test
	void roundingIsHalfUpAtThreeDecimals() {
		assertThat(LocationResolver.round3(12.97649)).isEqualTo(12.976);
		assertThat(LocationResolver.round3(12.9765)).isEqualTo(12.977);
		assertThat(LocationResolver.round3(-77.6045)).isEqualTo(-77.605);
		assertThat(LocationResolver.round3(77.0)).isEqualTo(77.0);
	}

	@Test
	void aLocalityDoesNotLookForANearbyLocalityToLabelIt() {
		UUID id = UUID.randomUUID();
		when(reference.locality(id)).thenReturn(Optional.of(locality(id, "Koramangala", 12.93, 77.62)));
		when(reference.resolveArea(12.93, 77.62)).thenReturn(new AreaResolution(CITY, ZONE, true, 0));

		resolver.resolve(id, null, null);

		verify(reference).locality(id);
		verify(reference).resolveArea(12.93, 77.62);
		verifyNoMoreInteractions(reference);
	}

}
