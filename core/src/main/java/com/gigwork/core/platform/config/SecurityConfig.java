package com.gigwork.core.platform.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.gigwork.core.platform.PublicEndpoints;

import jakarta.servlet.DispatcherType;

/**
 * Every endpoint needs a valid access token unless some module declared it public with a {@link PublicEndpoints}
 * bean (ADR-0009). Public means "no token required": a request with an invalid or expired token is still refused
 * with 401 (the bearer token filter runs before the authorization check). Stateless: no session, no cookie, no
 * CSRF token, because the API is called with a bearer token and never with browser credentials.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
			List<PublicEndpoints> publicEndpoints) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> {
					// error pages carry no data and must not turn a 415 or 404 into a 401
					requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
					publicEndpoints.stream().flatMap(declared -> declared.rules().stream()).forEach(rule -> {
						if (rule.method() == null) {
							requests.requestMatchers(rule.pattern()).permitAll();
						}
						else {
							requests.requestMatchers(rule.method(), rule.pattern()).permitAll();
						}
					});
					requests.anyRequest().authenticated();
				})
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));
		return http.build();
	}

}
