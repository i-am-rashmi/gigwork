package com.gigwork.core.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.security.KeyedHasher;
import com.gigwork.core.identity.security.RefreshTokens;
import com.gigwork.core.identity.support.CapturingOtpSender;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestNumbers;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;

/** Login and refresh over HTTP: the full path from a phone number to a working session (FR-1, FR-3, ADR-0005). */
@IdentityIntegrationTest
class TokenEndpointsTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	KeyedHasher hasher;
	@Autowired
	CapturingOtpSender sender;
	@Autowired
	MutableClock clock;

	@BeforeEach
	void resetClock() {
		clock.reset();
	}

	record Login(String body, String accessToken, String refreshToken, boolean newAccount) {
	}

	private static String json(String... pairs) {
		StringBuilder json = new StringBuilder("{");
		for (int i = 0; i < pairs.length; i += 2) {
			json.append(i > 0 ? "," : "").append('"').append(pairs[i]).append("\":\"").append(pairs[i + 1]).append('"');
		}
		return json.append('}').toString();
	}

	private ResultActions postJson(String url, String body) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private Login login(MobileNumber mobile) throws Exception {
		String requested = postJson("/v1/auth/otp/request", json("mobile", mobile.value())).andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString();
		String challengeId = JsonPath.read(requested, "$.challengeId");
		String body = postJson("/v1/auth/otp/verify",
				json("mobile", mobile.value(), "challengeId", challengeId, "code", sender.lastCode(mobile)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return new Login(body, JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"),
				JsonPath.read(body, "$.newAccount"));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return postJson("/v1/auth/token/refresh", json("refreshToken", refreshToken));
	}

	private UUID accountOf(MobileNumber mobile) {
		return jdbc.sql("SELECT account_id FROM identity.mobile_identity WHERE mobile_hash = :hash")
				.param("hash", hasher.hashMobile(mobile)).query(UUID.class).single();
	}

	private byte[] storedRefreshHash(UUID account) {
		return jdbc.sql("SELECT refresh_token_hash FROM identity.session WHERE account_id = :a ORDER BY created_at DESC LIMIT 1")
				.param("a", account).query(byte[].class).single();
	}

	// --- verify

	@Test
	void signUpReturnsAccessAndRefreshTokens() throws Exception {
		MobileNumber mobile = TestNumbers.next();

		Login login = login(mobile);

		assertThat(login.newAccount()).isTrue();
		UUID account = accountOf(mobile);
		SignedJWT jwt = SignedJWT.parse(login.accessToken());
		assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(account.toString());
		assertThat(JsonPath.<String>read(login.body(), "$.tokenType")).isEqualTo("Bearer");
		assertThat(JsonPath.<Integer>read(login.body(), "$.expiresIn")).isEqualTo(900);
		assertThat(login.refreshToken()).hasSize(RefreshTokens.TOKEN_LENGTH);
		String sessionId = jdbc.sql("SELECT id FROM identity.session WHERE account_id = :a").param("a", account)
				.query(UUID.class).single().toString();
		assertThat(jwt.getJWTClaimsSet().getStringClaim("sid")).isEqualTo(sessionId);
	}

	@Test
	void theRefreshTokenIsStoredOnlyAsAHash() throws Exception {
		MobileNumber mobile = TestNumbers.next();

		Login login = login(mobile);

		byte[] stored = storedRefreshHash(accountOf(mobile));
		assertThat(stored).hasSize(32).isEqualTo(RefreshTokens.hash(login.refreshToken()))
				.isNotEqualTo(login.refreshToken().getBytes());
	}

	@Test
	void aReturningUserGetsTheSameAccountAndANewSession() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		Login first = login(mobile);

		clock.advance(Duration.ofSeconds(30)); // a new code is allowed 30 seconds after the previous one
		Login second = login(mobile);

		assertThat(first.newAccount()).isTrue();
		assertThat(second.newAccount()).isFalse();
		assertThat(SignedJWT.parse(second.accessToken()).getJWTClaimsSet().getSubject())
				.isEqualTo(SignedJWT.parse(first.accessToken()).getJWTClaimsSet().getSubject());
		assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
		assertThat(jdbc.sql("SELECT count(*) FROM identity.session WHERE account_id = :a").param("a", accountOf(mobile))
				.query(Long.class).single()).isEqualTo(2);
	}

	@Test
	void tokenResponsesAreNeverCached() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		postJson("/v1/auth/otp/request", json("mobile", mobile.value()));
		clock.advance(Duration.ofSeconds(30)); // a new code is allowed 30 seconds after the previous one
		String challenge = JsonPath.read(
				postJson("/v1/auth/otp/request", json("mobile", mobile.value())).andReturn().getResponse().getContentAsString(),
				"$.challengeId");

		postJson("/v1/auth/otp/verify", json("mobile", mobile.value(), "challengeId", challenge, "code", sender.lastCode(mobile)))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
	}

	@Test
	void aWrongCodeGives401WithoutEchoingAnything() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		String requested = postJson("/v1/auth/otp/request", json("mobile", mobile.value())).andReturn().getResponse()
				.getContentAsString();
		String challenge = JsonPath.read(requested, "$.challengeId");
		String right = sender.lastCode(mobile);
		String wrong = right.equals("000000") ? "000001" : "000000";

		postJson("/v1/auth/otp/verify", json("mobile", mobile.value(), "challengeId", challenge, "code", wrong))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Invalid code"))
				.andExpect(jsonPath("$.accessToken").doesNotExist());
	}

	@ParameterizedTest
	@ValueSource(strings = { "12345", "1234567", "12ab56", "", " 123456" })
	void aCodeThatIsNotSixDigitsIs400(String code) throws Exception {
		postJson("/v1/auth/otp/verify",
				json("mobile", TestNumbers.next().value(), "challengeId", UUID.randomUUID().toString(), "code", code))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aMissingOrMalformedChallengeIdIs400() throws Exception {
		postJson("/v1/auth/otp/verify", json("mobile", TestNumbers.next().value(), "code", "123456"))
				.andExpect(status().isBadRequest());
		postJson("/v1/auth/otp/verify", json("mobile", TestNumbers.next().value(), "challengeId", "not-a-uuid", "code", "123456"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aBadMobileNumberOnVerifyIs400() throws Exception {
		postJson("/v1/auth/otp/verify", json("mobile", "+14155550123", "challengeId", UUID.randomUUID().toString(), "code", "123456"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Invalid mobile number"));
	}

	// --- refresh

	@Test
	void refreshRotatesTheTokenPair() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		Login login = login(mobile);
		clock.advance(Duration.ofMinutes(1));

		String body = refresh(login.refreshToken()).andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(900))
				.andReturn().getResponse().getContentAsString();

		String newRefresh = JsonPath.read(body, "$.refreshToken");
		String newAccess = JsonPath.read(body, "$.accessToken");
		assertThat(newRefresh).isNotEqualTo(login.refreshToken()).hasSize(RefreshTokens.TOKEN_LENGTH);
		assertThat(newAccess).isNotEqualTo(login.accessToken());
		var oldClaims = SignedJWT.parse(login.accessToken()).getJWTClaimsSet();
		var newClaims = SignedJWT.parse(newAccess).getJWTClaimsSet();
		assertThat(newClaims.getSubject()).isEqualTo(oldClaims.getSubject());
		assertThat(newClaims.getStringClaim("sid")).as("same session").isEqualTo(oldClaims.getStringClaim("sid"));
		assertThat(storedRefreshHash(accountOf(mobile))).isEqualTo(RefreshTokens.hash(newRefresh));
		assertThat(jdbc.sql("SELECT count(*) FROM identity.session WHERE account_id = :a").param("a", accountOf(mobile))
				.query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void theOldRefreshTokenStopsWorkingAndTheNewOneWorks() throws Exception {
		Login login = login(TestNumbers.next());
		String newRefresh = JsonPath.read(
				refresh(login.refreshToken()).andReturn().getResponse().getContentAsString(), "$.refreshToken");

		refresh(login.refreshToken()).andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Invalid refresh token"));
		refresh(newRefresh).andExpect(status().isOk());
	}

	@Test
	void aSessionExpiresAfterSevenDaysWithoutUse() throws Exception {
		Login login = login(TestNumbers.next());
		clock.advance(Duration.ofDays(7));

		refresh(login.refreshToken()).andExpect(status().isUnauthorized());
	}

	@Test
	void aSessionStillWorksJustBeforeTheIdleLimit() throws Exception {
		Login login = login(TestNumbers.next());
		clock.advance(Duration.ofDays(7).minusSeconds(1));

		refresh(login.refreshToken()).andExpect(status().isOk());
	}

	@Test
	void useKeepsASessionAliveUntilThirtyDaysInTotal() throws Exception {
		Login login = login(TestNumbers.next());
		String token = login.refreshToken();

		for (int i = 0; i < 5; i++) {
			clock.advance(Duration.ofDays(5));
			token = JsonPath.read(refresh(token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
					"$.refreshToken");
		}
		clock.advance(Duration.ofDays(5));

		refresh(token).andExpect(status().isUnauthorized());
	}

	@Test
	void aRevokedSessionCannotRefresh() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		Login login = login(mobile);
		jdbc.sql("UPDATE identity.session SET revoked_at = now() WHERE account_id = :a").param("a", accountOf(mobile)).update();

		refresh(login.refreshToken()).andExpect(status().isUnauthorized());
	}

	@Test
	void anAccountThatIsNotActiveCannotRefreshAndNothingRotates() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		Login login = login(mobile);
		UUID account = accountOf(mobile);
		jdbc.sql("UPDATE identity.account SET status = 'DELETION_REQUESTED' WHERE id = :a").param("a", account).update();

		refresh(login.refreshToken()).andExpect(status().isUnauthorized());

		assertThat(storedRefreshHash(account)).isEqualTo(RefreshTokens.hash(login.refreshToken()));
	}

	@ParameterizedTest
	@ValueSource(strings = { "short", "not-a-valid-token", "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!",
			"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" })
	void unknownOrMalformedTokensGive401(String token) throws Exception {
		refresh(token).andExpect(status().isUnauthorized());
	}

	@Test
	void anAccessTokenIsNotAcceptedAsARefreshToken() throws Exception {
		Login login = login(TestNumbers.next());

		refresh(login.accessToken()).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"refreshToken\":\"\"}", "{\"refreshToken\":null}" })
	void aMissingRefreshTokenIs400(String json) throws Exception {
		postJson("/v1/auth/token/refresh", json).andExpect(status().isBadRequest());
	}

}
