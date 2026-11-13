package com.gigwork.core.identity.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.gigwork.core.identity.config.IdentityProperties.Jwt;
import com.gigwork.core.identity.config.IdentityProperties.Otp;
import com.gigwork.core.identity.config.IdentityProperties.Otp.SenderType;
import com.gigwork.core.identity.config.IdentityProperties.Session;

class DevConfigurationGuardTest {

	private static final String DEV_KEY = DevConfigurationGuard.DEV_VALUE_MARKER + "mobile-hash-key-0000";
	private static final String REAL_KEY = "a-real-key-0123456789-abcdefghijklmnopqrstuvwxyz";

	private static IdentityProperties properties(String mobileKey, String otpKey, SenderType sender, String jwtPrivateKey) {
		return new IdentityProperties(mobileKey, otpKey, new Otp(sender),
				new Jwt(jwtPrivateKey, null, "gigwork-core", Duration.ofMinutes(15)),
				new Session(Duration.ofDays(7), Duration.ofDays(30)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "prod", "production", "staging", "dev" })
	void refusesDevKeysInAnyNonLocalNonTestProfile(String profile) {
		var props = properties(DEV_KEY, REAL_KEY, SenderType.NONE, null);

		assertThatThrownBy(() -> DevConfigurationGuard.verify(props, new String[] { profile }))
				.isInstanceOf(DevConfigurationException.class)
				.hasMessageContaining("gigwork.identity.mobile-hash-key");
	}

	@Test
	void refusesDevKeysWithNoActiveProfile() {
		var props = properties(REAL_KEY, DEV_KEY, SenderType.NONE, null);

		assertThatThrownBy(() -> DevConfigurationGuard.verify(props, new String[0]))
				.isInstanceOf(DevConfigurationException.class)
				.hasMessageContaining("gigwork.identity.otp-hash-key");
	}

	@Test
	void refusesDevJwtKey() {
		var props = properties(REAL_KEY, REAL_KEY, SenderType.NONE, DEV_KEY);

		assertThatThrownBy(() -> DevConfigurationGuard.verify(props, new String[] { "prod" }))
				.isInstanceOf(DevConfigurationException.class)
				.hasMessageContaining("gigwork.identity.jwt.private-key");
	}

	@ParameterizedTest
	@ValueSource(strings = { "prod", "staging" })
	void refusesDevSenderEvenWithRealKeys(String profile) {
		var props = properties(REAL_KEY, REAL_KEY, SenderType.DEV, null);

		assertThatThrownBy(() -> DevConfigurationGuard.verify(props, new String[] { profile }))
				.isInstanceOf(DevConfigurationException.class)
				.hasMessageContaining("otp.sender=dev");
	}

	@Test
	void failsClosedWhenALocalProfileIsMixedWithAnotherProfile() {
		var props = properties(DEV_KEY, DEV_KEY, SenderType.DEV, null);

		assertThatThrownBy(() -> DevConfigurationGuard.verify(props, new String[] { "local", "prod" }))
				.isInstanceOf(DevConfigurationException.class);
	}

	@Test
	void neverPrintsKeyValues() {
		var props = properties(DEV_KEY, REAL_KEY, SenderType.NONE, null);

		assertThatThrownBy(() -> DevConfigurationGuard.verify(props, new String[] { "prod" }))
				.hasMessageNotContaining(DEV_KEY)
				.hasMessageNotContaining(REAL_KEY);
	}

	@ParameterizedTest
	@ValueSource(strings = { "local", "test" })
	void allowsDevConfigurationInLocalAndTest(String profile) {
		var props = properties(DEV_KEY, DEV_KEY, SenderType.DEV, DEV_KEY);

		assertThatCode(() -> DevConfigurationGuard.verify(props, new String[] { profile })).doesNotThrowAnyException();
	}

	@Test
	void allowsRealKeysAndNoSenderInProduction() {
		var props = properties(REAL_KEY, REAL_KEY, SenderType.NONE, null);

		assertThatCode(() -> DevConfigurationGuard.verify(props, new String[] { "prod" })).doesNotThrowAnyException();
	}

}
