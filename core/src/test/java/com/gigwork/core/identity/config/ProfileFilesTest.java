package com.gigwork.core.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * Guards the profile files themselves: every fake key must carry the marker the startup guard looks for, and the
 * default configuration must hold no secrets and keep the dev OTP sender off (ADR-0005, NFR-45).
 */
class ProfileFilesTest {

	private static Properties load(String file) {
		var factory = new YamlPropertiesFactoryBean();
		factory.setResources(new ClassPathResource(file));
		return factory.getObject();
	}

	private static boolean isKey(String name) {
		return name.startsWith("gigwork.identity.") && name.endsWith("key");
	}

	@Test
	void defaultConfigurationHoldsNoKeysAndNoDevSender() {
		var properties = load("application.yaml");

		assertThat(properties.stringPropertyNames()).noneMatch(ProfileFilesTest::isKey);
		assertThat(properties.getProperty("gigwork.identity.otp.sender")).isEqualTo("none");
	}

	@Test
	void everyKeyInTheLocalProfileIsMarkedAsFake() {
		var properties = load("application-local.yaml");

		var keys = properties.stringPropertyNames().stream().filter(ProfileFilesTest::isKey).toList();
		assertThat(keys).isNotEmpty();
		keys.forEach(key -> assertThat(properties.getProperty(key)).as(key)
				.startsWith(DevConfigurationGuard.DEV_VALUE_MARKER));
	}

	@Test
	void everyKeyInTheTestProfileIsMarkedAsFake() {
		var properties = load("application-test.yaml");

		var keys = properties.stringPropertyNames().stream().filter(ProfileFilesTest::isKey).toList();
		assertThat(keys).isNotEmpty();
		keys.forEach(key -> assertThat(properties.getProperty(key)).as(key)
				.startsWith(DevConfigurationGuard.DEV_VALUE_MARKER));
	}

	@Test
	void apiDocsAndSwaggerUiAreOffByDefaultAndOnlyOnInTheLocalProfile() {
		var defaults = load("application.yaml");
		var local = load("application-local.yaml");

		assertThat(defaults.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
		assertThat(defaults.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
		assertThat(local.getProperty("springdoc.api-docs.enabled")).isEqualTo("true");
		assertThat(local.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("true");
		assertThat(load("application-test.yaml").getProperty("springdoc.api-docs.enabled")).isNull();
	}

	@Test
	void devSenderIsEnabledInTheLocalProfileOnly() {
		assertThat(load("application-local.yaml").getProperty("gigwork.identity.otp.sender")).isEqualTo("dev");
		assertThat(load("application-test.yaml").getProperty("gigwork.identity.otp.sender")).isNull();
	}

}
