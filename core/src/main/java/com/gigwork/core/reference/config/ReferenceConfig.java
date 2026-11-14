package com.gigwork.core.reference.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gigwork.core.reference.LocationResolver;
import com.gigwork.core.reference.ReferenceData;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReferenceProperties.class)
class ReferenceConfig {

	@Bean
	LocationResolver locationResolver(ReferenceData reference, ReferenceProperties properties) {
		return new LocationResolver(reference, properties.location().maxDistanceFromZoneKm(),
				properties.location().labelRadiusMeters());
	}

}
