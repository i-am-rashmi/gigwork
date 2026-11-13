package com.gigwork.core.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gigwork.core.platform.PublicEndpoints;

/** The endpoints a visitor may call without a token: sign up / log in and token refresh (FR-1, FR-3). */
@Configuration(proxyBeanMethods = false)
class IdentityPublicEndpoints {

	@Bean
	PublicEndpoints authEndpoints() {
		return PublicEndpoints.post("/auth/otp/request", "/auth/otp/verify", "/auth/token/refresh");
	}

}
