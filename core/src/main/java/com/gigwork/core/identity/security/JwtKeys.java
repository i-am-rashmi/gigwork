package com.gigwork.core.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.gigwork.core.identity.config.DevConfigurationGuard;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;

/**
 * The RSA key pair that signs and verifies access tokens (ADR-0005). In production both halves come from the
 * secrets manager as PEM through configuration (NFR-45). In the local and test profiles a pair is generated at
 * startup when none is configured; tokens then stop working on restart, which is fine for development. Any
 * other profile without a configured pair fails to start.
 */
public record JwtKeys(RSAPrivateKey privateKey, RSAPublicKey publicKey) {

	private static final int GENERATED_KEY_BITS = 2048;

	/**
	 * @param privateKeyPem PKCS#8 PEM, or blank
	 * @param publicKeyPem  X.509 (SubjectPublicKeyInfo) PEM, or blank
	 */
	public static JwtKeys from(String privateKeyPem, String publicKeyPem, String[] activeProfiles) {
		boolean hasPrivate = hasText(privateKeyPem);
		boolean hasPublic = hasText(publicKeyPem);
		if (hasPrivate != hasPublic) {
			throw new JwtKeyConfigurationException(
					"Configure both gigwork.identity.jwt.private-key and gigwork.identity.jwt.public-key, or neither");
		}
		JwtKeys keys;
		if (hasPrivate) {
			keys = new JwtKeys(parsePrivate(privateKeyPem), parsePublic(publicKeyPem));
		}
		else if (DevConfigurationGuard.onlyLocalOrTestProfilesActive(activeProfiles)) {
			keys = generate();
		}
		else {
			throw new JwtKeyConfigurationException("gigwork.identity.jwt.private-key and public-key are required "
					+ "unless only the 'local' and/or 'test' profiles are active");
		}
		keys.requireMatchingPair();
		return keys;
	}

	/** Signs and verifies a probe, so a mismatched pair fails at startup, not on the first login. */
	private void requireMatchingPair() {
		try {
			JWSObject probe = new JWSObject(new JWSHeader(JWSAlgorithm.RS256), new Payload("probe"));
			probe.sign(new RSASSASigner(privateKey));
			if (!probe.verify(new RSASSAVerifier(publicKey))) {
				throw new JwtKeyConfigurationException("The JWT private key does not match the public key");
			}
		}
		catch (JOSEException | RuntimeException e) {
			if (e instanceof JwtKeyConfigurationException configurationException) {
				throw configurationException;
			}
			throw new JwtKeyConfigurationException("The JWT key pair cannot sign and verify", e);
		}
	}

	private static JwtKeys generate() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(GENERATED_KEY_BITS);
			KeyPair pair = generator.generateKeyPair();
			return new JwtKeys((RSAPrivateKey) pair.getPrivate(), (RSAPublicKey) pair.getPublic());
		}
		catch (GeneralSecurityException e) {
			throw new JwtKeyConfigurationException("Could not generate a development JWT key pair", e);
		}
	}

	private static RSAPrivateKey parsePrivate(String pem) {
		try {
			byte[] der = decodePem(pem, "PRIVATE KEY");
			return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
		}
		catch (GeneralSecurityException | RuntimeException e) {
			throw new JwtKeyConfigurationException("gigwork.identity.jwt.private-key is not a valid PKCS#8 RSA PEM key");
		}
	}

	private static RSAPublicKey parsePublic(String pem) {
		try {
			byte[] der = decodePem(pem, "PUBLIC KEY");
			return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
		}
		catch (GeneralSecurityException | RuntimeException e) {
			throw new JwtKeyConfigurationException("gigwork.identity.jwt.public-key is not a valid X.509 RSA PEM key");
		}
	}

	/** Accepts real newlines or the two-character sequence \n, which is how multi-line values often arrive in env vars. */
	private static byte[] decodePem(String pem, String type) {
		String body = pem.replace("\\n", "\n")
				.replace("-----BEGIN " + type + "-----", "")
				.replace("-----END " + type + "-----", "")
				.replaceAll("\\s", "");
		return Base64.getDecoder().decode(body.getBytes(StandardCharsets.US_ASCII));
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	/** Never print key material. */
	@Override
	public String toString() {
		return "JwtKeys[RSA]";
	}

}
