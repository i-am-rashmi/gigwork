package com.gigwork.core.identity.security;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.gigwork.core.identity.config.IdentityProperties;
import com.gigwork.core.platform.ApiPaths;

import jakarta.servlet.DispatcherType;

/**
 * Every endpoint needs a valid access token except the three auth endpoints, the health probes and the API docs
 * (the docs exist only where springdoc is switched on, i.e. the local profile). Stateless: no session, no cookie,
 * no CSRF token, because the API is called with a bearer token and never with browser credentials.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	/** Tolerated difference between the clocks of the instances that issue and check tokens. */
	private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						// error pages carry no data and must not turn a 415 or 404 into a 401
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(HttpMethod.POST, ApiPaths.V1 + "/auth/otp/request",
								ApiPaths.V1 + "/auth/otp/verify", ApiPaths.V1 + "/auth/token/refresh")
						.permitAll()
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
						.permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));
		return http.build();
	}

	/**
	 * Verifies access tokens locally with the public key (ADR-0005): signature (RS256 only, which rules out
	 * "none" and key-confusion tricks), a present and unreached expiry, the issuer, and that the token names an
	 * account and a session.
	 */
	@Bean
	JwtDecoder jwtDecoder(JwtKeys keys, IdentityProperties properties, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
				.signatureAlgorithm(SignatureAlgorithm.RS256).build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
		timestamps.setClock(clock);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
				new JwtIssuerValidator(properties.jwt().issuer()), new RequiredClaimsValidator()));
		return decoder;
	}

	/**
	 * Our tokens always carry an expiry, an account ID as subject and a session ID; anything else is not ours.
	 * The expiry is required explicitly because the standard timestamp check treats a missing one as "never
	 * expires".
	 */
	private static final class RequiredClaimsValidator implements OAuth2TokenValidator<Jwt> {

		private static final OAuth2Error INVALID = new OAuth2Error("invalid_token",
				"The token must have an expiry and identify an account and a session", null);

		@Override
		public OAuth2TokenValidatorResult validate(Jwt jwt) {
			boolean complete = jwt.getExpiresAt() != null && isUuid(jwt.getSubject())
					&& isUuid(jwt.getClaimAsString(AccessTokenIssuer.SESSION_ID_CLAIM));
			return complete ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INVALID);
		}

		private static boolean isUuid(String value) {
			if (value == null) {
				return false;
			}
			try {
				UUID.fromString(value);
				return true;
			}
			catch (IllegalArgumentException e) {
				return false;
			}
		}

	}

}
