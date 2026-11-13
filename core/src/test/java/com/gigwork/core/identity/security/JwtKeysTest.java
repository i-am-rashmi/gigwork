package com.gigwork.core.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class JwtKeysTest {

	private static KeyPair pair() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		return generator.generateKeyPair();
	}

	private static String pem(String type, byte[] der) {
		return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
				+ "\n-----END " + type + "-----\n";
	}

	private static String privatePem(KeyPair pair) {
		return pem("PRIVATE KEY", pair.getPrivate().getEncoded());
	}

	private static String publicPem(KeyPair pair) {
		return pem("PUBLIC KEY", pair.getPublic().getEncoded());
	}

	private static final String[] PROD = { "prod" };

	@Test
	void usesAConfiguredPair() throws Exception {
		KeyPair pair = pair();

		JwtKeys keys = JwtKeys.from(privatePem(pair), publicPem(pair), PROD);

		assertThat(keys.publicKey().getEncoded()).isEqualTo(pair.getPublic().getEncoded());
		assertThat(keys.privateKey().getEncoded()).isEqualTo(pair.getPrivate().getEncoded());
	}

	@Test
	void acceptsPemWithEscapedNewlinesAsFoundInEnvironmentVariables() throws Exception {
		KeyPair pair = pair();

		JwtKeys keys = JwtKeys.from(privatePem(pair).replace("\n", "\\n"), publicPem(pair).replace("\n", "\\n"), PROD);

		assertThat(keys.publicKey().getEncoded()).isEqualTo(pair.getPublic().getEncoded());
	}

	@Test
	void generatesAPairForLocalAndTestWhenNoneIsConfigured() {
		JwtKeys local = JwtKeys.from(null, null, new String[] { "local" });
		JwtKeys test = JwtKeys.from("  ", "", new String[] { "test" });

		assertThat(local.publicKey().getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
		assertThat(local.publicKey()).isNotEqualTo(test.publicKey());
	}

	@Test
	void refusesToGenerateAPairInProductionOrTheDefaultProfile() {
		assertThatThrownBy(() -> JwtKeys.from(null, null, PROD)).isInstanceOf(JwtKeyConfigurationException.class);
		assertThatThrownBy(() -> JwtKeys.from(null, null, new String[0]))
				.isInstanceOf(JwtKeyConfigurationException.class);
		assertThatThrownBy(() -> JwtKeys.from(null, null, new String[] { "local", "prod" }))
				.isInstanceOf(JwtKeyConfigurationException.class);
	}

	@Test
	void refusesHalfAPair() throws Exception {
		KeyPair pair = pair();

		assertThatThrownBy(() -> JwtKeys.from(privatePem(pair), null, new String[] { "local" }))
				.isInstanceOf(JwtKeyConfigurationException.class);
		assertThatThrownBy(() -> JwtKeys.from(null, publicPem(pair), new String[] { "local" }))
				.isInstanceOf(JwtKeyConfigurationException.class);
	}

	@Test
	void refusesAPrivateKeyThatDoesNotMatchThePublicKey() throws Exception {
		KeyPair one = pair();
		KeyPair other = pair();

		assertThatThrownBy(() -> JwtKeys.from(privatePem(one), publicPem(other), PROD))
				.isInstanceOf(JwtKeyConfigurationException.class).hasMessageContaining("does not match");
	}

	@Test
	void refusesGarbageWithoutEchoingIt() throws Exception {
		KeyPair pair = pair();
		String garbage = "-----BEGIN PRIVATE KEY-----\nTOP-SECRET-GARBAGE\n-----END PRIVATE KEY-----";

		assertThatThrownBy(() -> JwtKeys.from(garbage, publicPem(pair), PROD))
				.isInstanceOf(JwtKeyConfigurationException.class)
				.hasMessageContaining("private-key").hasMessageNotContaining("TOP-SECRET-GARBAGE");
	}

	@Test
	void neverPrintsKeyMaterial() {
		JwtKeys keys = JwtKeys.from(null, null, new String[] { "test" });

		assertThat(keys.toString()).isEqualTo("JwtKeys[RSA]");
	}

}
