package com.gigwork.core.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.support.CapturingOtpSender;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestNumbers;

/** The HTTP side of the per-number limit (NFR-28): 429 with Retry-After and a fixed message. */
@IdentityIntegrationTest
class OtpRateLimitEndpointTest {

	private static final String URL = "/v1/auth/otp/request";

	@Autowired
	MockMvc mvc;
	@Autowired
	MutableClock clock;
	@Autowired
	CapturingOtpSender sender;

	@BeforeEach
	void reset() {
		clock.reset();
		sender.reset();
	}

	@AfterEach
	void resetSender() {
		sender.reset();
	}

	private ResultActions request(MobileNumber mobile) throws Exception {
		return mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
				.content("{\"mobile\":\"" + mobile.value() + "\"}"));
	}

	@Test
	void aSecondRequestWithinThirtySecondsGets429WithRetryAfterAndNoNumber() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		request(mobile).andExpect(status().isAccepted());
		clock.advance(Duration.ofSeconds(10));

		String response = request(mobile)
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "20"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Too many code requests"))
				.andExpect(jsonPath("$.detail").value("Too many code requests. Please try again later."))
				.andReturn().getResponse().getContentAsString();

		assertThat(response).doesNotContain(mobile.value()).doesNotContain(mobile.value().substring(3));
	}

	@Test
	void theFourthRequestInTheWindowGets429AndALaterOneIsAccepted() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		for (int i = 0; i < 3; i++) {
			request(mobile).andExpect(status().isAccepted());
			clock.advance(Duration.ofSeconds(31));
		}

		request(mobile).andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", Long.toString(15 * 60 - 93)));

		clock.advance(Duration.ofSeconds(15 * 60 - 93));
		request(mobile).andExpect(status().isAccepted());
	}

	@Test
	void aFailedSendIs503ButStillCountsTowardTheLimit() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		sender.failSends(true);
		for (int i = 0; i < 3; i++) {
			request(mobile).andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.title").value("Code could not be sent"));
			clock.advance(Duration.ofSeconds(31));
		}

		request(mobile).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
	}

}
