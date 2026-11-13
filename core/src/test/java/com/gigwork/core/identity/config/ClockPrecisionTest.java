package com.gigwork.core.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** The production clock only delivers whole microseconds: PostgreSQL's timestamptz rounds anything finer. */
class ClockPrecisionTest {

	@Test
	void nanosecondsAreTruncatedNotRounded() {
		Clock nanos = Clock.fixed(Instant.parse("2026-01-01T10:00:00.123456789Z"), ZoneOffset.UTC);

		Instant instant = IdentityConfig.microsecondPrecision(nanos).instant();

		assertThat(instant).isEqualTo(Instant.parse("2026-01-01T10:00:00.123456Z"));
	}

	@Test
	void theSystemClockBeanHasNoSubMicrosecondDigits() {
		Clock clock = new IdentityConfig().clock();

		for (int i = 0; i < 1000; i++) {
			assertThat(clock.instant().getNano() % 1000).isZero();
		}
	}

}
