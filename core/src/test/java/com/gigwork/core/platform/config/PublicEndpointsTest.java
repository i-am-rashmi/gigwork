package com.gigwork.core.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.identity.security.AccessTokenIssuer;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.platform.PublicEndpoints;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * ADR-0009. The first test is the review gate: it lists every public endpoint of the application, so adding one
 * (in any module) fails it until the expectation is changed on purpose. The others check that public means "no
 * token required", not "any token accepted".
 */
@IdentityIntegrationTest
class PublicEndpointsTest {

	@Autowired
	List<PublicEndpoints> declarations;
	@Autowired
	MockMvc mvc;
	@Autowired
	AccessTokenIssuer issuer;
	@Autowired
	MutableClock clock;

	@BeforeEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void listsEveryPublicEndpointOfTheApplication() {
		List<String> all = declarations.stream().flatMap(d -> d.rules().stream()).map(Object::toString).toList();

		assertThat(all).containsExactlyInAnyOrder(
				// identity
				"POST /v1/auth/otp/request", "POST /v1/auth/otp/verify", "POST /v1/auth/token/refresh",
				// reference data
				"GET /v1/categories", "GET /v1/languages", "GET /v1/cities", "GET /v1/cities/*/zones",
				"GET /v1/localities",
				// platform: health probes and API docs (the docs exist only where springdoc is on)
				"ANY /actuator/health", "ANY /actuator/health/**",
				"ANY /v3/api-docs", "ANY /v3/api-docs/**", "ANY /swagger-ui.html", "ANY /swagger-ui/**");
	}

	private String valid() {
		return "Bearer " + issuer.issue(UUID.randomUUID(), UUID.randomUUID(), clock.instant()).token();
	}

	private String expired() {
		String token = issuer.issue(UUID.randomUUID(), UUID.randomUUID(), clock.instant()).token();
		clock.advance(Duration.ofMinutes(16));
		return "Bearer " + token;
	}

	private String foreignKey() throws Exception {
		var generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		var key = (RSAPrivateKey) generator.generateKeyPair().getPrivate();
		JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("gigwork-core").subject(UUID.randomUUID().toString())
				.claim("sid", UUID.randomUUID().toString())
				.expirationTime(Date.from(clock.instant().plusSeconds(900))).build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
		jwt.sign(new RSASSASigner(key));
		return "Bearer " + jwt.serialize();
	}

	private int authEndpoint(String authorization) throws Exception {
		var request = post("/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON).content("{}");
		if (authorization != null) {
			request.header("Authorization", authorization);
		}
		return mvc.perform(request).andReturn().getResponse().getStatus();
	}

	private int health(String authorization) throws Exception {
		var request = get("/actuator/health");
		if (authorization != null) {
			request.header("Authorization", authorization);
		}
		return mvc.perform(request).andReturn().getResponse().getStatus();
	}

	@Test
	void aPublicEndpointWorksWithoutATokenAndWithAValidOne() throws Exception {
		assertThat(authEndpoint(null)).isEqualTo(400); // reached the controller (validation), not security
		assertThat(authEndpoint(valid())).isEqualTo(400);
		assertThat(health(null)).isEqualTo(200);
		assertThat(health(valid())).isEqualTo(200);
	}

	@Test
	void aPublicEndpointRefusesAnInvalidTokenWith401() throws Exception {
		assertThat(authEndpoint("Bearer garbage")).isEqualTo(401);
		assertThat(authEndpoint(foreignKey())).isEqualTo(401);
		assertThat(health("Bearer garbage")).isEqualTo(401);
		assertThat(health(foreignKey())).isEqualTo(401);
	}

	@Test
	void aPublicEndpointRefusesAnExpiredTokenWith401() throws Exception {
		assertThat(authEndpoint(expired())).isEqualTo(401);
		assertThat(health(expired())).isEqualTo(401);
	}

	@Test
	void aNonPublicPathStillNeedsAToken() throws Exception {
		mvc.perform(get("/v1/anything")).andExpect(status().isUnauthorized());
	}

}
