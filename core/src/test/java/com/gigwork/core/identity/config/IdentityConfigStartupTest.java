package com.gigwork.core.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.gigwork.core.identity.config.IdentityProperties.Otp.SenderType;

/** Startup behaviour of the identity configuration: required secrets, safe defaults and the dev guard. */
class IdentityConfigStartupTest {

	private static final String DEV_KEY = DevConfigurationGuard.DEV_VALUE_MARKER + "key-0123456789";
	private static final String REAL_KEY = "a-real-key-0123456789-abcdefghijklmnopqrstuvwxyz";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(IdentityConfig.class, DevConfigurationGuard.class);

	@Test
	void startsWithDefaultsOffAndNoSenderInProduction() {
		runner.withPropertyValues("spring.profiles.active=prod", "gigwork.identity.mobile-hash-key=" + REAL_KEY,
				"gigwork.identity.otp-hash-key=" + REAL_KEY).run(context -> {
					assertThat(context).hasNotFailed();
					var properties = context.getBean(IdentityProperties.class);
					assertThat(properties.otp().sender()).isEqualTo(SenderType.NONE);
					assertThat(properties.jwt().accessTokenTtl()).hasMinutes(15);
					assertThat(properties.session().idleTimeout()).hasDays(7);
					assertThat(properties.session().absoluteTimeout()).hasDays(30);
				});
	}

	@Test
	void failsWithoutSecrets() {
		runner.withPropertyValues("spring.profiles.active=prod").run(context -> assertThat(context).hasFailed());
	}

	@Test
	void failsWithAShortKey() {
		runner.withPropertyValues("spring.profiles.active=prod", "gigwork.identity.mobile-hash-key=too-short",
				"gigwork.identity.otp-hash-key=" + REAL_KEY).run(context -> assertThat(context).hasFailed());
	}

	@Test
	void refusesToStartWithDevKeysInProd() {
		runner.withPropertyValues("spring.profiles.active=prod", "gigwork.identity.mobile-hash-key=" + DEV_KEY,
				"gigwork.identity.otp-hash-key=" + REAL_KEY).run(context -> assertThat(context).getFailure()
						.hasRootCauseInstanceOf(DevConfigurationException.class));
	}

	@Test
	void refusesToStartWithDevKeysInTheDefaultProfile() {
		// surefire activates 'test' for the whole JVM; blank it to get the real default (no active profile)
		runner.withPropertyValues("spring.profiles.active=", "gigwork.identity.mobile-hash-key=" + DEV_KEY,
				"gigwork.identity.otp-hash-key=" + DEV_KEY).run(context -> assertThat(context).getFailure()
						.hasRootCauseInstanceOf(DevConfigurationException.class));
	}

	@Test
	void refusesToStartWithTheDevSenderInProd() {
		runner.withPropertyValues("spring.profiles.active=prod", "gigwork.identity.otp.sender=dev",
				"gigwork.identity.mobile-hash-key=" + REAL_KEY, "gigwork.identity.otp-hash-key=" + REAL_KEY)
				.run(context -> assertThat(context).getFailure()
						.hasRootCauseInstanceOf(DevConfigurationException.class));
	}

	@Test
	void startsWithDevKeysAndDevSenderInLocal() {
		runner.withPropertyValues("spring.profiles.active=local", "gigwork.identity.otp.sender=dev",
				"gigwork.identity.mobile-hash-key=" + DEV_KEY, "gigwork.identity.otp-hash-key=" + DEV_KEY)
				.run(context -> assertThat(context).hasNotFailed());
	}

}
