package com.gigwork.core.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.security.AccessTokenIssuer;
import com.gigwork.core.identity.security.KeyedHasher;
import com.gigwork.core.identity.support.CapturingOtpSender;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestNumbers;
import com.jayway.jsonpath.JsonPath;

@IdentityIntegrationTest
class OtpRequestEndpointTest {

	private static final String URL = "/v1/auth/otp/request";

	@Autowired
	MockMvc mvc;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	KeyedHasher hasher;
	@Autowired
	CapturingOtpSender sender;
	@Autowired
	AccessTokenIssuer tokens;
	@Autowired
	MutableClock clock;

	private static String body(String mobile) {
		return "{\"mobile\":\"" + mobile + "\"}";
	}

	private MvcResult request(String mobile) throws Exception {
		return mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(mobile))).andReturn();
	}

	private long challengeCount() {
		return jdbc.sql("SELECT count(*) FROM identity.otp_challenge").query(Long.class).single();
	}

	@Test
	void sendsACodeAndStoresOnlyItsKeyedHash() throws Exception {
		MobileNumber mobile = TestNumbers.next();

		MvcResult result = mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(mobile.value())))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.challengeId").isString())
				.andExpect(jsonPath("$.expiresInSeconds").value(300))
				.andExpect(jsonPath("$.code").doesNotExist())
				.andReturn();

		UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.challengeId"));
		String code = sender.lastCode(mobile);
		assertThat(code).matches("[0-9]{6}");
		var row = jdbc.sql("SELECT target_hash, code_hash, created_at, expires_at, attempts, used_at FROM identity.otp_challenge WHERE id = :id")
				.param("id", id)
				.query((rs, i) -> new Object[] { rs.getBytes("target_hash"), rs.getBytes("code_hash"),
						rs.getObject("created_at", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class),
						rs.getInt("attempts"), rs.getObject("used_at") })
				.single();
		assertThat((byte[]) row[0]).isEqualTo(hasher.hashMobile(mobile));
		assertThat((byte[]) row[1]).hasSize(32).isEqualTo(hasher.hashOtpCode(id, code)).isNotEqualTo(code.getBytes());
		assertThat(Duration.between((OffsetDateTime) row[2], (OffsetDateTime) row[3])).isEqualTo(Duration.ofMinutes(5));
		assertThat(row[4]).isEqualTo(0);
		assertThat(row[5]).isNull();
	}

	@Test
	void normalisesTheNumberBeforeSending() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		String national = mobile.value().substring(3);

		mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
				.content(body("0 " + national.substring(0, 5) + "-" + national.substring(5))))
				.andExpect(status().isAccepted());

		assertThat(sender.lastCode(mobile)).matches("[0-9]{6}");
	}

	@ParameterizedTest
	@ValueSource(strings = { "12345", "5876543210", "+14155550123", "not a number", "98765432101" })
	void rejectsNumbersThatAreNotIndianMobileNumbers(String mobile) throws Exception {
		long challengesBefore = challengeCount();
		int sentBefore = sender.sentCount();

		mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(mobile)))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Invalid mobile number"))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(mobile))));

		assertThat(challengeCount()).isEqualTo(challengesBefore);
		assertThat(sender.sentCount()).isEqualTo(sentBefore);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"mobile\":\"\"}", "{\"mobile\":\"   \"}", "{\"mobile\":null}", "" })
	void rejectsAMissingOrBlankNumber(String json) throws Exception {
		mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void aNewRequestSupersedesThePreviousCodeOfTheSameNumber() throws Exception {
		MobileNumber mobile = TestNumbers.next();

		UUID first = UUID.fromString(JsonPath.read(request(mobile.value()).getResponse().getContentAsString(), "$.challengeId"));
		UUID second = UUID.fromString(JsonPath.read(request(mobile.value()).getResponse().getContentAsString(), "$.challengeId"));

		assertThat(first).isNotEqualTo(second);
		assertThat(isUsed(first)).as("first challenge is ended").isTrue();
		assertThat(isUsed(second)).as("newest challenge is live").isFalse();
	}

	@Test
	void endpointIsOnlyReachableUnderTheVersionPrefix() throws Exception {
		String token = tokens.issue(UUID.randomUUID(), UUID.randomUUID(), clock.instant()).token();

		// with a valid token the route simply does not exist; without one nothing about routes is revealed
		mvc.perform(post("/auth/otp/request").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(body("9876543210")))
				.andExpect(status().isNotFound());
		mvc.perform(post("/auth/otp/request").contentType(MediaType.APPLICATION_JSON).content(body("9876543210")))
				.andExpect(status().isUnauthorized());
	}

	private boolean isUsed(UUID id) {
		return jdbc.sql("SELECT used_at IS NOT NULL FROM identity.otp_challenge WHERE id = :id").param("id", id)
				.query(Boolean.class).single();
	}

}
