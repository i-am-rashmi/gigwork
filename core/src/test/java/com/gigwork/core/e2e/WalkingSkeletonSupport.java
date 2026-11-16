package com.gigwork.core.e2e;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gigwork.core.hiring.HiringApiSupport;
import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.support.CapturingOtpSender;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.identity.support.TestNumbers;
import com.jayway.jsonpath.JsonPath;

/** Logs accounts in through the real OTP endpoints, so the end-to-end tests start where a user starts. */
final class WalkingSkeletonSupport {

	private final MockMvc mvc;
	private final CapturingOtpSender otp;

	WalkingSkeletonSupport(MockMvc mvc, CapturingOtpSender otp) {
		this.mvc = mvc;
		this.otp = otp;
	}

	/** A person who logged in: the account ID and the bearer token, as {@link TestAccount} so the shared builders accept it. */
	TestAccount login() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		String requestBody = "{\"mobile\":\"" + mobile.value() + "\"}";
		String challenge = body(mvc.perform(post("/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isAccepted()));
		UUID challengeId = UUID.fromString(JsonPath.read(challenge, "$.challengeId"));
		String verifyBody = "{\"mobile\":\"" + mobile.value() + "\",\"challengeId\":\"" + challengeId + "\",\"code\":\""
				+ otp.lastCode(mobile) + "\"}";
		String login = body(mvc.perform(post("/v1/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content(verifyBody))
				.andExpect(status().isOk()));
		String bearer = JsonPath.read(login, "$.accessToken");
		return new TestAccount(accountId(bearer), bearer);
	}

	/** The token's subject is the account ID (identity's contract). */
	private static UUID accountId(String bearer) {
		String payload = new String(java.util.Base64.getUrlDecoder().decode(bearer.split("\\.")[1]),
				java.nio.charset.StandardCharsets.UTF_8);
		return UUID.fromString(JsonPath.read(payload, "$.sub"));
	}

	ResultActions name(TestAccount account, String firstName) throws Exception {
		return mvc.perform(put("/v1/me/name").header("Authorization", account.header()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"" + firstName + "\"}"));
	}

	ResultActions calendar(TestAccount account, String from, String to) throws Exception {
		return mvc.perform(get("/v1/me/calendar").param("from", from).param("to", to).header("Authorization", account.header()));
	}

	ResultActions publicProfile(UUID accountId, String bearerHeader) throws Exception {
		var request = get("/v1/talent/" + accountId);
		if (bearerHeader != null) {
			request.header("Authorization", bearerHeader);
		}
		return mvc.perform(request);
	}

	static String body(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

	static UUID id(ResultActions result) throws Exception {
		return HiringApiSupport.id(result);
	}

	/** Polls until the condition holds (an event listener runs after the request's own transaction). */
	static void await(String what, BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("timed out waiting for: " + what);
	}

}
