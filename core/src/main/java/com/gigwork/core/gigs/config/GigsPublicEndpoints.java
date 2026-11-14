package com.gigwork.core.gigs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gigwork.core.platform.PublicEndpoints;

/**
 * Anyone can browse open gigs and read one (FR-9). A token, if sent, is still checked, and the response then also
 * shows the Poster's first name; what each caller sees is decided in the application layer (NFR-34, ADR-0009).
 */
@Configuration(proxyBeanMethods = false)
class GigsPublicEndpoints {

	@Bean
	PublicEndpoints gigBrowsing() {
		return PublicEndpoints.get("/gigs", "/gigs/*");
	}

}
