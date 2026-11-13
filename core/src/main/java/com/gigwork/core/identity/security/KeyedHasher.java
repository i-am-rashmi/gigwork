package com.gigwork.core.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.gigwork.core.identity.config.IdentityProperties;
import com.gigwork.core.identity.domain.MobileNumber;

/**
 * Keyed hashes (HMAC-SHA256) of the values Identity must find or check but never store readable:
 * <ul>
 * <li>the mobile number, used to find accounts and OTP challenges (NFR-44), with its own key;</li>
 * <li>an OTP code, bound to its challenge ID, with a different key. A plain hash of a six-digit code could be
 * reversed instantly from a leaked table; without the key it cannot.</li>
 * </ul>
 * Both results are 32 bytes. Comparisons must use {@link #sameHash}, which takes constant time.
 */
@Component
public class KeyedHasher {

	private static final String ALGORITHM = "HmacSHA256";

	private final SecretKeySpec mobileKey;
	private final SecretKeySpec otpKey;

	public KeyedHasher(IdentityProperties properties) {
		this.mobileKey = new SecretKeySpec(properties.mobileHashKey().getBytes(StandardCharsets.UTF_8), ALGORITHM);
		this.otpKey = new SecretKeySpec(properties.otpHashKey().getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	public byte[] hashMobile(MobileNumber mobile) {
		return hmac(mobileKey, mobile.value());
	}

	public byte[] hashOtpCode(UUID challengeId, String code) {
		return hmac(otpKey, challengeId + ":" + code);
	}

	public static boolean sameHash(byte[] a, byte[] b) {
		return MessageDigest.isEqual(a, b);
	}

	private static byte[] hmac(SecretKeySpec key, String message) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(ALGORITHM + " is not available", e);
		}
	}

}
