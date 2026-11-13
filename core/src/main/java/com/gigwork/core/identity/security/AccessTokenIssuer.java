package com.gigwork.core.identity.security;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.gigwork.core.identity.config.IdentityProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Issues the short-lived access token (ADR-0005): an RS256 JWT any service can verify with the public key.
 * Claims: {@code sub} = account ID, {@code sid} = session ID, {@code iss}, {@code iat}, {@code exp}, {@code jti}.
 * It carries no personal data: no mobile number, no name.
 */
@Component
public class AccessTokenIssuer {

	public static final String SESSION_ID_CLAIM = "sid";

	private final RSASSASigner signer;
	private final IdentityProperties.Jwt settings;

	AccessTokenIssuer(JwtKeys keys, IdentityProperties properties) {
		this.signer = new RSASSASigner(keys.privateKey());
		this.settings = properties.jwt();
	}

	public record IssuedAccessToken(String token, Instant expiresAt) {

		/** Never print a token (NFR-46). */
		@Override
		public String toString() {
			return "IssuedAccessToken[expiresAt=" + expiresAt + "]";
		}
	}

	public IssuedAccessToken issue(UUID accountId, UUID sessionId, Instant now) {
		Instant expiresAt = now.plus(settings.accessTokenTtl());
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(settings.issuer())
				.subject(accountId.toString())
				.claim(SESSION_ID_CLAIM, sessionId.toString())
				.issueTime(Date.from(now))
				.expirationTime(Date.from(expiresAt))
				.jwtID(UUID.randomUUID().toString())
				.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims);
		try {
			jwt.sign(signer);
		}
		catch (JOSEException e) {
			throw new JwtKeyConfigurationException("Could not sign the access token", e);
		}
		return new IssuedAccessToken(jwt.serialize(), expiresAt);
	}

}
