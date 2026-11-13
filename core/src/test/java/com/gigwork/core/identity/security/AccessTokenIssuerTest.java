package com.gigwork.core.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gigwork.core.identity.config.IdentityProperties;
import com.gigwork.core.identity.config.IdentityProperties.Jwt;
import com.gigwork.core.identity.config.IdentityProperties.Otp;
import com.gigwork.core.identity.config.IdentityProperties.Otp.SenderType;
import com.gigwork.core.identity.config.IdentityProperties.Session;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;

class AccessTokenIssuerTest {

	private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

	private final JwtKeys keys = JwtKeys.from(null, null, new String[] { "test" });
	private final AccessTokenIssuer issuer = issuer(keys, Duration.ofMinutes(15));

	private static AccessTokenIssuer issuer(JwtKeys keys, Duration ttl) {
		return new AccessTokenIssuer(keys,
				new IdentityProperties("k".repeat(32), "k".repeat(32), new Otp(SenderType.NONE),
						new Jwt(null, null, "gigwork-core", ttl), new Session(Duration.ofDays(7), Duration.ofDays(30))));
	}

	@Test
	void issuesASignedRs256TokenWithTheDocumentedClaims() throws Exception {
		UUID account = UUID.randomUUID();
		UUID session = UUID.randomUUID();

		var issued = issuer.issue(account, session, NOW);
		SignedJWT jwt = SignedJWT.parse(issued.token());

		assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
		assertThat(jwt.verify(new RSASSAVerifier(keys.publicKey()))).isTrue();
		var claims = jwt.getJWTClaimsSet();
		assertThat(claims.getIssuer()).isEqualTo("gigwork-core");
		assertThat(claims.getSubject()).isEqualTo(account.toString());
		assertThat(claims.getStringClaim("sid")).isEqualTo(session.toString());
		assertThat(claims.getIssueTime().toInstant()).isEqualTo(NOW);
		assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
		assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
		assertThat(claims.getJWTID()).isNotBlank();
	}

	@Test
	void carriesNoPersonalData() throws Exception {
		SignedJWT jwt = SignedJWT.parse(issuer.issue(UUID.randomUUID(), UUID.randomUUID(), NOW).token());

		assertThat(jwt.getJWTClaimsSet().getClaims().keySet())
				.containsExactlyInAnyOrder("iss", "sub", "sid", "iat", "exp", "jti");
	}

	@Test
	void everyTokenHasItsOwnId() throws ParseException {
		UUID account = UUID.randomUUID();
		UUID session = UUID.randomUUID();

		String first = SignedJWT.parse(issuer.issue(account, session, NOW).token()).getJWTClaimsSet().getJWTID();
		String second = SignedJWT.parse(issuer.issue(account, session, NOW).token()).getJWTClaimsSet().getJWTID();

		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void theLifetimeFollowsTheConfiguration() throws Exception {
		var shortLived = issuer(keys, Duration.ofMinutes(1)).issue(UUID.randomUUID(), UUID.randomUUID(), NOW);

		assertThat(SignedJWT.parse(shortLived.token()).getJWTClaimsSet().getExpirationTime().toInstant())
				.isEqualTo(NOW.plusSeconds(60));
	}

	@Test
	void aDifferentKeyDoesNotVerifyTheToken() throws Exception {
		JwtKeys other = JwtKeys.from(null, null, new String[] { "test" });
		SignedJWT jwt = SignedJWT.parse(issuer.issue(UUID.randomUUID(), UUID.randomUUID(), NOW).token());

		assertThat(jwt.verify(new RSASSAVerifier(other.publicKey()))).isFalse();
	}

	@Test
	void aTamperedPayloadDoesNotVerify() throws Exception {
		SignedJWT jwt = SignedJWT.parse(issuer.issue(UUID.randomUUID(), UUID.randomUUID(), NOW).token());
		// the real header and signature, but a payload naming a different account
		JWSObject forged = new JWSObject(jwt.getHeader().toBase64URL(),
				new Payload("{\"sub\":\"" + UUID.randomUUID() + "\"}").toBase64URL(), jwt.getSignature());

		assertThat(forged.verify(new RSASSAVerifier(keys.publicKey()))).isFalse();
	}

	@Test
	void theTokenNeverPrintsItself() {
		var issued = issuer.issue(UUID.randomUUID(), UUID.randomUUID(), NOW);

		assertThat(issued.toString()).doesNotContain(issued.token());
	}

}
