package com.gigwork.core.identity.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Configuration of the identity module. Secrets have no defaults: outside the local and test profiles the
 * application refuses to start without them (NFR-45). Set them through the environment, for example
 * {@code GIGWORK_IDENTITY_MOBILE_HASH_KEY}.
 *
 * @param mobileHashKey key for the HMAC-SHA256 of the normalised mobile number (account lookup, NFR-44)
 * @param otpHashKey    key for the HMAC-SHA256 of OTP codes; deliberately a different key from the mobile one
 */
@Validated
@ConfigurationProperties("gigwork.identity")
public record IdentityProperties(
		@NotBlank @Size(min = MIN_KEY_LENGTH, message = "must be at least 32 characters") String mobileHashKey,
		@NotBlank @Size(min = MIN_KEY_LENGTH, message = "must be at least 32 characters") String otpHashKey,
		@Valid @DefaultValue Otp otp,
		@Valid @DefaultValue Jwt jwt,
		@Valid @DefaultValue Session session) {

	static final int MIN_KEY_LENGTH = 32;

	/**
	 * @param sender      which {@code OtpSender} exists in the application; {@code none} is the default and
	 *                    delivers nothing
	 * @param sendTimeout how long to wait for the sender before the delivery counts as failed
	 */
	public record Otp(@NotNull @DefaultValue("none") SenderType sender,
			@NotNull @DefaultValue("5s") Duration sendTimeout) {

		static final Duration DEFAULT_SEND_TIMEOUT = Duration.ofSeconds(5);

		@ConstructorBinding
		public Otp {
		}

		public Otp(SenderType sender) {
			this(sender, DEFAULT_SEND_TIMEOUT);
		}

		public enum SenderType {
			/** No delivery: requesting a code fails with 503. The default everywhere except the local profile. */
			NONE,
			/** Development only: writes the code to the log. Refused outside the local and test profiles. */
			DEV
		}
	}

	/**
	 * @param privateKey PEM (PKCS#8) private key that signs access tokens; generated per run in local and test
	 * @param publicKey  PEM (X.509) public key that verifies them
	 */
	public record Jwt(String privateKey, String publicKey,
			@NotBlank @DefaultValue("gigwork-core") String issuer,
			@NotNull @DefaultValue("15m") Duration accessTokenTtl) {
	}

	public record Session(@NotNull @DefaultValue("7d") Duration idleTimeout,
			@NotNull @DefaultValue("30d") Duration absoluteTimeout) {
	}

}
