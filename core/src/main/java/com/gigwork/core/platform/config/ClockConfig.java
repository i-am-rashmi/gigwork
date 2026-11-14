package com.gigwork.core.platform.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

	/**
	 * The application's time source; tests replace it to move time. It ticks in whole microseconds, the precision
	 * of PostgreSQL's timestamptz: a nanosecond instant is rounded by the database on storage, so comparing it
	 * with the stored value is off by up to half a microsecond (on Linux, where clocks have nanosecond
	 * precision).
	 */
	@Bean
	Clock clock() {
		return microsecondPrecision(Clock.systemUTC());
	}

	static Clock microsecondPrecision(Clock source) {
		return Clock.tick(source, Duration.ofNanos(1000));
	}

}
