package com.gigwork.core.identity.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.otp.OtpSender;
import com.gigwork.core.identity.persistence.OtpChallengeRepository;
import com.gigwork.core.identity.security.KeyedHasher;

/**
 * One-time codes for mobile login (NFR-37): six digits, stored only as a keyed hash, valid for 5 minutes, at
 * most 5 attempts, and only the newest code of a number is live.
 *
 * <p>Nothing here logs a mobile number or a code (NFR-46); the development sender is the one exception.
 */
@Service
public class OtpService {

	public static final Duration CODE_LIFETIME = Duration.ofMinutes(5);

	private static final int CODE_DIGITS = 6;

	private final OtpChallengeRepository challenges;
	private final KeyedHasher hasher;
	private final OtpSender sender;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	OtpService(OtpChallengeRepository challenges, KeyedHasher hasher, OtpSender sender, Clock clock) {
		this.challenges = challenges;
		this.hasher = hasher;
		this.sender = sender;
		this.clock = clock;
	}

	public record IssuedChallenge(UUID challengeId, Instant expiresAt) {
	}

	/**
	 * Creates a challenge and hands the code to the sender. Delivery happens inside the transaction: if the sender
	 * fails, nothing is kept (the previous code stays live too).
	 */
	@Transactional
	public IssuedChallenge request(MobileNumber mobile) {
		Instant now = clock.instant();
		UUID id = UUID.randomUUID();
		String code = newCode();
		byte[] target = hasher.hashMobile(mobile);

		challenges.supersedeLive(target, now);
		challenges.insert(id, target, hasher.hashOtpCode(id, code), now, now.plus(CODE_LIFETIME));
		sender.send(mobile, code);
		return new IssuedChallenge(id, now.plus(CODE_LIFETIME));
	}

	/**
	 * Checks a code. Returns false for every failure (wrong, expired, used, out of attempts, wrong number,
	 * unknown challenge) and never throws for them: a thrown exception would roll back the attempt that was just
	 * counted and let a guesser retry for free. Call it from a transaction that commits on a false result.
	 */
	@Transactional
	public boolean verify(MobileNumber mobile, UUID challengeId, String code) {
		Instant now = clock.instant();
		Optional<byte[]> stored = challenges.recordAttempt(challengeId, hasher.hashMobile(mobile), now);
		if (stored.isEmpty()) {
			return false;
		}
		if (!KeyedHasher.sameHash(stored.get(), hasher.hashOtpCode(challengeId, code))) {
			return false;
		}
		return challenges.markUsed(challengeId, now);
	}

	private String newCode() {
		return String.format("%0" + CODE_DIGITS + "d", random.nextInt((int) Math.pow(10, CODE_DIGITS)));
	}

}
