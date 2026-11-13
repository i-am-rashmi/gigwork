package com.gigwork.core.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gigwork.core.identity.application.AuthenticationService;
import com.gigwork.core.identity.application.OtpService;
import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.support.CapturingOtpSender;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestNumbers;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

/**
 * "Protect all other endpoints": everything except the three auth endpoints and the health probes needs an access
 * token that is correctly signed (RS256 with our key), unexpired, from our issuer and about an account and session.
 * Each way of forging or misusing a token has a test, because a gap here would be a full authentication bypass.
 */
@IdentityIntegrationTest
class SecurityTest {

	/** Not routed to any controller: with a valid token the answer is 404, without one it must be 401. */
	private static final String PROTECTED = "/v1/anything";

	@Autowired
	MockMvc mvc;
	@Autowired
	AccessTokenIssuer issuer;
	@Autowired
	JwtKeys keys;
	@Autowired
	MutableClock clock;
	@Autowired
	OtpService otp;
	@Autowired
	AuthenticationService authentication;
	@Autowired
	CapturingOtpSender sender;

	@BeforeEach
	void resetClock() {
		clock.reset();
	}

	private String validToken() {
		return issuer.issue(UUID.randomUUID(), UUID.randomUUID(), clock.instant()).token();
	}

	private ResultActions getWith(String path, String authorization) throws Exception {
		MockHttpServletRequestBuilder request = get(path);
		if (authorization != null) {
			request.header("Authorization", authorization);
		}
		return mvc.perform(request);
	}

	private static JWTClaimsSet.Builder claims(Instant now) {
		return new JWTClaimsSet.Builder().issuer("gigwork-core").subject(UUID.randomUUID().toString())
				.claim("sid", UUID.randomUUID().toString()).issueTime(Date.from(now))
				.expirationTime(Date.from(now.plus(Duration.ofMinutes(15)))).jwtID(UUID.randomUUID().toString());
	}

	private static String sign(JWSSigner signer, JWSAlgorithm algorithm, JWTClaimsSet claims) throws Exception {
		SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
		jwt.sign(signer);
		return jwt.serialize();
	}

	// --- what is public

	@Test
	void theAuthEndpointsAndHealthProbesNeedNoToken() throws Exception {
		mvc.perform(post("/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()); // reached the controller: validation answered, not security
		mvc.perform(post("/v1/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/v1/auth/token/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
		mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
		mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
	}

	// --- what is protected

	@Test
	void everythingElseNeedsAToken() throws Exception {
		getWith(PROTECTED, null).andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")));
		mvc.perform(post(PROTECTED)).andExpect(status().isUnauthorized());
		getWith("/actuator/metrics", null).andExpect(status().isUnauthorized());
		getWith("/actuator/info", null).andExpect(status().isUnauthorized());
		getWith("/v3/api-docs", null).andExpect(status().isNotFound()); // public path, but docs are off by default
		getWith("/", null).andExpect(status().isUnauthorized());
	}

	@Test
	void theAuthEndpointsAcceptOnlyPost() throws Exception {
		getWith("/v1/auth/otp/request", null).andExpect(status().isUnauthorized());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/v1/auth/token/refresh"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aValidTokenIsAccepted() throws Exception {
		getWith(PROTECTED, "Bearer " + validToken()).andExpect(status().isNotFound()); // past security: no such route
		getWith("/actuator/info", "Bearer " + validToken()).andExpect(status().isOk());
		getWith("/actuator/metrics", "Bearer " + validToken()).andExpect(status().isOk());
	}

	@Test
	void aTokenFromARealLoginIsAccepted() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		UUID challenge = otp.request(mobile).challengeId();
		String token = authentication.verifyOtp(mobile, challenge, sender.lastCode(mobile)).orElseThrow().tokens()
				.accessToken();

		getWith("/actuator/info", "Bearer " + token).andExpect(status().isOk());
	}

	@Test
	void theApiIsStateless() throws Exception {
		getWith("/actuator/info", "Bearer " + validToken()).andExpect(status().isOk())
				.andExpect(header().doesNotExist("Set-Cookie"));
	}

	// --- misuse of the Authorization header

	@Test
	void notABearerTokenIsRefused() throws Exception {
		getWith("/actuator/info", "Bearer garbage").andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")));
		getWith("/actuator/info", "Basic dXNlcjpwYXNz").andExpect(status().isUnauthorized());
		getWith("/actuator/info", "Bearer ").andExpect(status().isUnauthorized());
		getWith("/actuator/info", validToken()).andExpect(status().isUnauthorized()); // scheme missing
	}

	@Test
	void aTokenInTheQueryStringIsNotAccepted() throws Exception {
		mvc.perform(get("/actuator/info").param("access_token", validToken())).andExpect(status().isUnauthorized());
	}

	@Test
	void aRefreshTokenIsNotAnAccessToken() throws Exception {
		getWith("/actuator/info", "Bearer " + RefreshTokens.generate()).andExpect(status().isUnauthorized());
	}

	// --- time

	@Test
	void aTokenExpiresAfterFifteenMinutes() throws Exception {
		String token = validToken();

		clock.advance(Duration.ofMinutes(15).plusSeconds(31)); // 15 minutes plus the 30 s clock-skew allowance

		getWith("/actuator/info", "Bearer " + token).andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")));
	}

	@Test
	void aTokenStillWorksWithinTheClockSkewAllowance() throws Exception {
		String token = validToken();

		clock.advance(Duration.ofMinutes(15).plusSeconds(20));

		getWith("/actuator/info", "Bearer " + token).andExpect(status().isOk());
	}

	// --- forgery

	@Test
	void aTokenSignedWithAnotherKeyIsRefused() throws Exception {
		var generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		RSAPrivateKey attackerKey = (RSAPrivateKey) generator.generateKeyPair().getPrivate();

		String forged = sign(new RSASSASigner(attackerKey), JWSAlgorithm.RS256, claims(clock.instant()).build());

		getWith("/actuator/info", "Bearer " + forged).andExpect(status().isUnauthorized());
	}

	@Test
	void anUnsignedTokenIsRefused() throws Exception {
		String unsigned = new PlainJWT(claims(clock.instant()).build()).serialize();

		getWith("/actuator/info", "Bearer " + unsigned).andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenSignedWithTheEncodedPublicKeyAsAnHmacSecretIsRefused() throws Exception {
		// the classic algorithm-confusion attack: the public key is public, so anyone can use it as an HS256 secret
		String forged = sign(new MACSigner(keys.publicKey().getEncoded()), JWSAlgorithm.HS256,
				claims(clock.instant()).build());

		getWith("/actuator/info", "Bearer " + forged).andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenWithATamperedPayloadIsRefused() throws Exception {
		String[] parts = validToken().split("\\.");
		String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
				("{\"iss\":\"gigwork-core\",\"sub\":\"" + UUID.randomUUID() + "\",\"sid\":\"" + UUID.randomUUID()
						+ "\",\"exp\":" + (clock.instant().getEpochSecond() + 900) + "}").getBytes());

		getWith("/actuator/info", "Bearer " + parts[0] + "." + forgedPayload + "." + parts[2])
				.andExpect(status().isUnauthorized());
	}

	// --- genuine signature, wrong content (only possible with our key, so this guards against our own mistakes)

	@Test
	void aTokenFromAnotherIssuerIsRefused() throws Exception {
		String token = sign(new RSASSASigner(keys.privateKey()), JWSAlgorithm.RS256,
				claims(clock.instant()).issuer("someone-else").build());

		getWith("/actuator/info", "Bearer " + token).andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenWithoutASessionIsRefused() throws Exception {
		JWTClaimsSet withoutSession = new JWTClaimsSet.Builder().issuer("gigwork-core")
				.subject(UUID.randomUUID().toString()).issueTime(Date.from(clock.instant()))
				.expirationTime(Date.from(clock.instant().plusSeconds(900))).build();

		getWith("/actuator/info", "Bearer " + sign(new RSASSASigner(keys.privateKey()), JWSAlgorithm.RS256, withoutSession))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenWhoseSubjectIsNotAnAccountIdIsRefused() throws Exception {
		String token = sign(new RSASSASigner(keys.privateKey()), JWSAlgorithm.RS256,
				claims(clock.instant()).subject("admin").build());

		getWith("/actuator/info", "Bearer " + token).andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenWithoutAnExpiryIsRefused() throws Exception {
		JWTClaimsSet noExpiry = new JWTClaimsSet.Builder().issuer("gigwork-core").subject(UUID.randomUUID().toString())
				.claim("sid", UUID.randomUUID().toString()).build();

		getWith("/actuator/info", "Bearer " + sign(new RSASSASigner(keys.privateKey()), JWSAlgorithm.RS256, noExpiry))
				.andExpect(status().isUnauthorized());
	}

}
