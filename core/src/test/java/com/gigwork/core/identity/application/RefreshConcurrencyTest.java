package com.gigwork.core.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.gigwork.core.identity.application.AuthenticationService.LoginResult;
import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.security.RefreshTokens;
import com.gigwork.core.identity.support.CapturingOtpSender;
import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.TestNumbers;

/**
 * ADR-0005: refresh tokens rotate on every use and "concurrent refreshes from the same device" must be handled
 * carefully. Rotation is one conditional UPDATE, so of many simultaneous refreshes with the same token exactly
 * one wins and the others are refused (reuse detection that would revoke the session is a follow-up).
 */
@IdentityIntegrationTest
class RefreshConcurrencyTest {

	@Autowired
	OtpService otp;
	@Autowired
	AuthenticationService authentication;
	@Autowired
	SessionService sessions;
	@Autowired
	CapturingOtpSender sender;
	@Autowired
	JdbcClient jdbc;

	@RepeatedTest(3)
	void twentySimultaneousRefreshesWithOneTokenSucceedExactlyOnce() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		UUID challenge = otp.request(mobile).challengeId();
		LoginResult login = authentication.verifyOtp(mobile, challenge, sender.lastCode(mobile)).orElseThrow();
		String token = login.tokens().refreshToken();

		List<Optional<AuthTokens>> results = Concurrently.runAll(20, () -> sessions.refresh(token));

		List<AuthTokens> winners = results.stream().flatMap(Optional::stream).toList();
		assertThat(winners).as("exactly one refresh wins").hasSize(1);
		byte[] stored = jdbc.sql("SELECT refresh_token_hash FROM identity.session WHERE account_id = :a")
				.param("a", login.accountId()).query(byte[].class).single();
		assertThat(stored).as("the session holds the winner's new token").isEqualTo(RefreshTokens.hash(winners.get(0).refreshToken()));
		assertThat(sessions.refresh(token)).as("the old token stays dead").isEmpty();
		assertThat(sessions.refresh(winners.get(0).refreshToken())).as("the new one works").isPresent();
	}

}
