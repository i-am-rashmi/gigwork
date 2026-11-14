package com.gigwork.core.identity.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the OTP sender and the clock in integration tests. Production keeps exactly one sender bean. */
@TestConfiguration(proxyBeanMethods = false)
public class IdentityTestConfig {

	@Bean
	@Primary
	CapturingOtpSender capturingOtpSender() {
		return new CapturingOtpSender();
	}

	@Bean
	TestAccounts testAccounts(org.springframework.jdbc.core.simple.JdbcClient jdbc,
			com.gigwork.core.identity.security.AccessTokenIssuer issuer, java.time.Clock clock) {
		return new TestAccounts(jdbc, issuer, clock);
	}

	@Bean
	@Primary
	MutableClock mutableClock() {
		return new MutableClock();
	}

}
