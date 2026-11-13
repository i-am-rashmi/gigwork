package com.gigwork.core.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.domain.OtpDeliveryUnavailableException;
import com.gigwork.core.identity.security.KeyedHasher;
import com.gigwork.core.identity.support.CapturingOtpSender;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.TestNumbers;

/** A sender that does not answer in time counts as a failed delivery: 503, and the challenge is marked failed. */
@IdentityIntegrationTest
@TestPropertySource(properties = "gigwork.identity.otp.send-timeout=300ms")
class OtpSendTimeoutTest {

	@Autowired
	OtpService otp;
	@Autowired
	CapturingOtpSender sender;
	@Autowired
	KeyedHasher hasher;
	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void resetSender() {
		sender.reset();
	}

	@Test
	void aSenderThatTakesTooLongFailsTheDelivery() {
		MobileNumber mobile = TestNumbers.next();
		sender.delaySends(Duration.ofSeconds(3));

		long started = System.nanoTime();
		assertThatThrownBy(() -> otp.request(mobile)).isInstanceOf(OtpDeliveryUnavailableException.class);
		Duration waited = Duration.ofNanos(System.nanoTime() - started);

		assertThat(waited).as("gave up at the timeout, not when the sender returned").isLessThan(Duration.ofSeconds(2));
		long failed = jdbc.sql("SELECT count(*) FROM identity.otp_challenge WHERE target_hash = :hash AND delivery_failed_at IS NOT NULL")
				.param("hash", hasher.hashMobile(mobile)).query(Long.class).single();
		assertThat(failed).isEqualTo(1);
	}

}
