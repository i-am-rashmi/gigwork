package com.gigwork.core.identity.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.gigwork.core.identity.config.IdentityProperties.Otp.SenderType;

/**
 * Refuses to start the application with development-only configuration (ADR-0005): the development OTP sender,
 * or any key carrying the {@value #DEV_VALUE_MARKER} marker used by the fake keys of the local and test profiles.
 *
 * <p>The check fails closed: it passes only if at least one profile is active and every active profile is
 * {@code local} or {@code test}. No active profile (the default profile) counts as production-like. Messages
 * name the property but never print its value.
 */
@Component
public class DevConfigurationGuard {

	/** Every fake key in application-local.yaml and the test profile starts with this. */
	public static final String DEV_VALUE_MARKER = "dev-only-not-a-secret-";

	private static final Set<String> DEV_FRIENDLY_PROFILES = Set.of("local", "test");

	public DevConfigurationGuard(IdentityProperties properties, Environment environment) {
		verify(properties, environment.getActiveProfiles());
	}

	static void verify(IdentityProperties properties, String[] activeProfiles) {
		if (isDevFriendly(activeProfiles)) {
			return;
		}
		List<String> problems = new ArrayList<>();
		if (properties.otp().sender() == SenderType.DEV) {
			problems.add("gigwork.identity.otp.sender=dev");
		}
		keys(properties).forEach((name, value) -> {
			if (value != null && value.contains(DEV_VALUE_MARKER)) {
				problems.add(name + " is a development-only key");
			}
		});
		if (!problems.isEmpty()) {
			throw new DevConfigurationException("Development-only configuration is not allowed unless the active "
					+ "profiles are only 'local' and/or 'test' (active: " + List.of(activeProfiles) + "): "
					+ String.join("; ", problems));
		}
	}

	private static boolean isDevFriendly(String[] activeProfiles) {
		return activeProfiles.length > 0 && Set.of(activeProfiles).stream().allMatch(DEV_FRIENDLY_PROFILES::contains);
	}

	private static Map<String, String> keys(IdentityProperties properties) {
		Map<String, String> keys = new LinkedHashMap<>();
		keys.put("gigwork.identity.mobile-hash-key", properties.mobileHashKey());
		keys.put("gigwork.identity.otp-hash-key", properties.otpHashKey());
		keys.put("gigwork.identity.jwt.private-key", properties.jwt().privateKey());
		keys.put("gigwork.identity.jwt.public-key", properties.jwt().publicKey());
		return keys;
	}

}
