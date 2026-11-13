package com.gigwork.core.identity.security;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import com.gigwork.core.identity.config.IdentityProperties;

/**
 * Identity issues the access tokens, so identity also knows how to verify them. The platform module's security
 * configuration uses this {@link JwtDecoder} bean without depending on identity (ADR-0009).
 */
@Configuration(proxyBeanMethods = false)
class JwtDecoderConfig {

	/** Tolerated difference between the clocks of the instances that issue and check tokens. */
	private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

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
