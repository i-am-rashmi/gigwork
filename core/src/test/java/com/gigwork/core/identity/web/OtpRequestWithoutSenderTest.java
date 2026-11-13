package com.gigwork.core.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.TestcontainersConfiguration;
import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.otp.OtpSender;
import com.gigwork.core.identity.security.KeyedHasher;
import com.gigwork.core.identity.support.TestNumbers;

/**
 * The application exactly as configured by default (test profile, no sender override): with no real sender
 * configured, requesting a code must fail loudly and keep nothing, never pretend to work.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OtpRequestWithoutSenderTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	KeyedHasher hasher;
	@Autowired
	OtpSender sender;

	@Test
	void theDefaultSenderIsTheUnavailableOne() {
		assertThat(sender.getClass().getSimpleName()).isEqualTo("UnavailableOtpSender");
	}

	@Test
	void answers503AndKeepsNoChallenge() throws Exception {
		MobileNumber mobile = TestNumbers.next();

		mvc.perform(post("/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
				.content("{\"mobile\":\"" + mobile.value() + "\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Code could not be sent"));

		long challenges = jdbc.sql("SELECT count(*) FROM identity.otp_challenge WHERE target_hash = :hash")
				.param("hash", hasher.hashMobile(mobile)).query(Long.class).single();
		assertThat(challenges).isZero();
	}

}
