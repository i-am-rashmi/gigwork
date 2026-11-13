package com.gigwork.core.identity.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfig {

	/** The time source for OTP expiry and sessions; tests replace it to move time. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
