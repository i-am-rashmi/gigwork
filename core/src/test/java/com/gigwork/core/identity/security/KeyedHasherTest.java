package com.gigwork.core.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gigwork.core.identity.config.IdentityProperties;
import com.gigwork.core.identity.config.IdentityProperties.Jwt;
import com.gigwork.core.identity.config.IdentityProperties.Otp;
import com.gigwork.core.identity.config.IdentityProperties.Otp.SenderType;
import com.gigwork.core.identity.config.IdentityProperties.Session;
import com.gigwork.core.identity.domain.MobileNumber;

class KeyedHasherTest {

	private static final String MOBILE_KEY = "dev-only-not-a-secret-test-mobile-hash-key";
	private static final String OTP_KEY = "dev-only-not-a-secret-test-otp-hash-key";
	private static final UUID CHALLENGE = UUID.fromString("11111111-2222-3333-4444-555555555555");

	private static KeyedHasher hasher(String mobileKey, String otpKey) {
		return new KeyedHasher(new IdentityProperties(mobileKey, otpKey, new Otp(SenderType.NONE),
				new Jwt(null, null, "gigwork-core", Duration.ofMinutes(15)),
				new Session(Duration.ofDays(7), Duration.ofDays(30))));
	}

	private final KeyedHasher hasher = hasher(MOBILE_KEY, OTP_KEY);

	@Test
	void mobileHashMatchesAnIndependentHmacSha256() {
		// expected values computed outside Java: python3 hmac.new(key, message, sha256)
		assertThat(HexFormat.of().formatHex(hasher.hashMobile(MobileNumber.parse("9876543210"))))
				.isEqualTo("cc653d61fec7e7d31c8da0d8476accdcee93a999b0fdea51247f0cf27e70c882");
	}

	@Test
	void otpHashMatchesAnIndependentHmacSha256() {
		assertThat(HexFormat.of().formatHex(hasher.hashOtpCode(CHALLENGE, "123456")))
				.isEqualTo("a73ab406b2f8ee938c842f18029244156570e46dc93aa8331dea1495dd45cc54");
	}

	@Test
	void hashesAre32BytesAndDeterministic() {
		var number = MobileNumber.parse("9876543210");

		assertThat(hasher.hashMobile(number)).hasSize(32).isEqualTo(hasher.hashMobile(number));
		assertThat(hasher.hashOtpCode(CHALLENGE, "123456")).hasSize(32)
				.isEqualTo(hasher.hashOtpCode(CHALLENGE, "123456"));
	}

	@Test
	void everyNumberFormatGivesTheSameHash() {
		assertThat(hasher.hashMobile(MobileNumber.parse("+91 98765-43210")))
				.isEqualTo(hasher.hashMobile(MobileNumber.parse("09876543210")));
	}

	@Test
	void differentInputsGiveDifferentHashes() {
		assertThat(hasher.hashMobile(MobileNumber.parse("9876543210")))
				.isNotEqualTo(hasher.hashMobile(MobileNumber.parse("9876543211")));
		assertThat(hasher.hashOtpCode(CHALLENGE, "123456")).isNotEqualTo(hasher.hashOtpCode(CHALLENGE, "123457"));
	}

	@Test
	void otpHashIsBoundToItsChallenge() {
		assertThat(hasher.hashOtpCode(CHALLENGE, "123456"))
				.isNotEqualTo(hasher.hashOtpCode(UUID.randomUUID(), "123456"));
	}

	@Test
	void theHashDependsOnTheKey() {
		var other = hasher("dev-only-not-a-secret-another-mobile-key", "dev-only-not-a-secret-another-otp-key");

		assertThat(other.hashMobile(MobileNumber.parse("9876543210")))
				.isNotEqualTo(hasher.hashMobile(MobileNumber.parse("9876543210")));
		assertThat(other.hashOtpCode(CHALLENGE, "123456")).isNotEqualTo(hasher.hashOtpCode(CHALLENGE, "123456"));
	}

	@Test
	void mobileAndOtpUseSeparateKeys() {
		// with one key shared, the mobile hash of "x" could be replayed as an OTP hash of "x"
		var sharedKey = hasher(MOBILE_KEY, MOBILE_KEY);

		assertThat(hasher.hashMobile(MobileNumber.parse("9876543210")))
				.isEqualTo(sharedKey.hashMobile(MobileNumber.parse("9876543210")));
		assertThat(hasher.hashOtpCode(CHALLENGE, "123456")).isNotEqualTo(sharedKey.hashOtpCode(CHALLENGE, "123456"));
	}

	@Test
	void sameHashComparesContents() {
		byte[] a = hasher.hashOtpCode(CHALLENGE, "123456");

		assertThat(KeyedHasher.sameHash(a, hasher.hashOtpCode(CHALLENGE, "123456"))).isTrue();
		assertThat(KeyedHasher.sameHash(a, hasher.hashOtpCode(CHALLENGE, "654321"))).isFalse();
		assertThat(KeyedHasher.sameHash(a, new byte[0])).isFalse();
	}

}
