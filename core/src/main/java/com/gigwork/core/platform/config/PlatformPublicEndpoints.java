package com.gigwork.core.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gigwork.core.platform.PublicEndpoints;

/** The public infrastructure paths: health probes and the API docs (which exist only where springdoc is on). */
@Configuration(proxyBeanMethods = false)
class PlatformPublicEndpoints {

	@Bean
	PublicEndpoints healthProbes() {
		return PublicEndpoints.infrastructure("/actuator/health", "/actuator/health/**");
	}

	@Bean
	PublicEndpoints apiDocs() {
		return PublicEndpoints.infrastructure("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html",
				"/swagger-ui/**");
	}

}
