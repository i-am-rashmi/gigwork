package com.gigwork.core.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.gigwork.core.TestcontainersConfiguration;
import com.gigwork.core.availability.application.AddBusyBlock;
import com.gigwork.core.availability.application.BusyBlockService;
import com.gigwork.core.profile.TalentProfileSummary;
import com.gigwork.core.profile.TalentProfiles;
import com.gigwork.core.reference.CityInfo;
import com.gigwork.core.reference.ReferenceData;

/**
 * The Availability module on its own, against real PostgreSQL: it starts with its own beans plus what it asks other
 * modules for (profiles and reference data, stubbed here, and the platform clock).
 */
@ApplicationModuleTest
@Import({ TestcontainersConfiguration.class, AvailabilityModuleTest.Platform.class })
class AvailabilityModuleTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class Platform {

		@Bean
		Clock clock() {
			return Clock.systemUTC();
		}

	}

	@Autowired
	Availability availability;
	@Autowired
	BusyBlockService busyBlocks;
	@MockitoBean
	TalentProfiles profiles;
	@MockitoBean
	ReferenceData reference;

	@Test
	void theModuleBooksAndReportsConflicts() {
		UUID talent = UUID.randomUUID();
		Instant start = Instant.parse("2030-05-01T10:00:00Z");

		UUID booking = availability.book(talent, UUID.randomUUID(), start, start.plusSeconds(3600));

		assertThat(booking).isNotNull();
		assertThat(availability.conflicts(talent, start, start.plusSeconds(60)).hasConflict()).isTrue();
		assertThat(availability.conflicts(talent, start.plusSeconds(3600), start.plusSeconds(7200)).hasConflict()).isFalse();
	}

	@Test
	void anAllDayBlockUsesTheZoneOfTheTalentsCity() {
		UUID talent = UUID.randomUUID();
		UUID city = UUID.randomUUID();
		when(profiles.summary(talent)).thenReturn(Optional.of(new TalentProfileSummary(talent, "ACTIVE", city)));
		when(reference.city(city)).thenReturn(Optional.of(new CityInfo(city, "dubai", "Dubai", "Asia/Dubai")));

		var entry = busyBlocks.add(talent, new AddBusyBlock(null, null, true, java.time.LocalDate.now().plusDays(10), java.time.LocalDate.now().plusDays(10)));

		assertThat(entry.end().getEpochSecond() - entry.start().getEpochSecond()).isEqualTo(24 * 3600);
		assertThat(entry.start().atZone(java.time.ZoneId.of("Asia/Dubai")).toLocalTime()).isEqualTo(java.time.LocalTime.MIDNIGHT);
	}

}
