package com.gigwork.core.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Opaque refresh tokens (ADR-0005): 256 random bits, base64url. Only a SHA-256 hash is ever stored. A plain hash
 * is enough here, unlike OTP codes, because the token is unguessable: there is nothing to brute-force.
 */
public final class RefreshTokens {

	/** 32 bytes in unpadded base64url. */
	public static final int TOKEN_LENGTH = 43;

	private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{" + TOKEN_LENGTH + "}");
	private static final SecureRandom RANDOM = new SecureRandom();

	private RefreshTokens() {
	}

	public static String generate() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** Cheap check before touching the database: anything else cannot be a token we issued. */
	public static boolean hasValidFormat(String token) {
		return token != null && FORMAT.matcher(token).matches();
	}

	public static byte[] hash(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

}
