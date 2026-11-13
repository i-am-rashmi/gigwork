package com.gigwork.core.identity.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfig {

	/**
	 * The time source for OTP expiry, limits and sessions; tests replace it to move time. It ticks in whole
	 * microseconds, the precision of PostgreSQL's timestamptz: a nanosecond instant is rounded by the database
	 * on storage, so comparing it with the stored value is off by up to half a microsecond (on Linux, where
	 * clocks have nanosecond precision).
	 */
	@Bean
	Clock clock() {
		return microsecondPrecision(Clock.systemUTC());
	}

	static Clock microsecondPrecision(Clock source) {
		return Clock.tick(source, Duration.ofNanos(1000));
	}

}
