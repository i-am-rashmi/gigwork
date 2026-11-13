package com.gigwork.core.identity.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.gigwork.core.identity.config.IdentityProperties;

@Configuration(proxyBeanMethods = false)
class JwtKeyConfig {

	private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

	@Bean
	JwtKeys jwtKeys(IdentityProperties properties, Environment environment) {
		var jwt = properties.jwt();
		JwtKeys keys = JwtKeys.from(jwt.privateKey(), jwt.publicKey(), environment.getActiveProfiles());
		if (jwt.privateKey() == null || jwt.privateKey().isBlank()) {
			log.warn("No JWT key pair configured: generated a temporary one. Access tokens stop working on restart.");
		}
		return keys;
	}

}
