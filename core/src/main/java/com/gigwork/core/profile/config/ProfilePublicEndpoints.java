package com.gigwork.core.profile.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gigwork.core.platform.PublicEndpoints;

/**
 * A Talent's profile can be viewed without logging in (FR-9). A token, if sent, is still checked, and the view then
 * shows more (first name and portfolio links); what each viewer sees is decided in the application layer (NFR-34).
 */
@Configuration(proxyBeanMethods = false)
class ProfilePublicEndpoints {

	@Bean
	PublicEndpoints talentProfileView() {
		return PublicEndpoints.get("/talent/*");
	}

}
