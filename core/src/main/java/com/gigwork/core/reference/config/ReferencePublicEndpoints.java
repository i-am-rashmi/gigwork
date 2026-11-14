package com.gigwork.core.reference.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gigwork.core.platform.PublicEndpoints;

/** Reference data is for everyone, visitors included (ADR-0009): categories, languages, cities, zones, localities. */
@Configuration(proxyBeanMethods = false)
class ReferencePublicEndpoints {

	@Bean
	PublicEndpoints referenceEndpoints() {
		return PublicEndpoints.get("/categories", "/languages", "/cities", "/cities/*/zones", "/localities");
	}

}
