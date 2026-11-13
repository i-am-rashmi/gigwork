package com.gigwork.core.identity.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.identity.config.IdentityProperties;
import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.domain.OtpDeliveryUnavailableException;
import com.gigwork.core.identity.domain.OtpRateLimitedException;
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

	/** NFR-28: at most this many codes per number within {@link #LIMIT_WINDOW}... */
	public static final int MAX_CODES_PER_WINDOW = 3;
	public static final Duration LIMIT_WINDOW = Duration.ofMinutes(15);
	/** ...and a new code only this long after the previous one. */
	public static final Duration RESEND_INTERVAL = Duration.ofSeconds(30);

	private static final int CODE_DIGITS = 6;

	private static final ExecutorService SENDERS = Executors.newVirtualThreadPerTaskExecutor();

	private final OtpChallengeRepository challenges;
	private final KeyedHasher hasher;
	private final OtpSender sender;
	private final Clock clock;
	private final TransactionTemplate tx;
	private final Duration sendTimeout;
	private final SecureRandom random = new SecureRandom();

	OtpService(OtpChallengeRepository challenges, KeyedHasher hasher, OtpSender sender, Clock clock,
			PlatformTransactionManager transactionManager, IdentityProperties properties) {
		this.challenges = challenges;
		this.hasher = hasher;
		this.sender = sender;
		this.clock = clock;
		this.tx = new TransactionTemplate(transactionManager);
		this.sendTimeout = properties.otp().sendTimeout();
	}

	public record IssuedChallenge(UUID challengeId, Instant expiresAt) {
	}

	private record NewChallenge(UUID id, String code, Instant createdAt) {
	}

	/**
	 * Issues a code in three steps, so no network call ever happens inside a database transaction:
	 * <ol>
	 * <li>transaction 1: check the per-number limit and insert the challenge, under an advisory lock so the limit
	 * is exact however many requests arrive at once;</li>
	 * <li>send, outside any transaction, with a timeout;</li>
	 * <li>transaction 2: on success end the number's older live challenges; on failure mark the new challenge as
	 * failed (unusable, but it still counts toward the limit) and leave the previous code valid.</li>
	 * </ol>
	 *
	 * @throws OtpRateLimitedException         over the limit (NFR-28)
	 * @throws OtpDeliveryUnavailableException the code could not be delivered
	 */
	public IssuedChallenge request(MobileNumber mobile) {
		byte[] target = hasher.hashMobile(mobile);
		NewChallenge created = tx.execute(status -> createChallenge(target));

		boolean delivered = deliver(mobile, created.code());

		Instant now = clock.instant();
		if (!delivered) {
			tx.executeWithoutResult(status -> challenges.markDeliveryFailed(created.id(), now));
			throw new OtpDeliveryUnavailableException();
		}
		tx.executeWithoutResult(status -> challenges.supersedeOlder(target, created.createdAt(), now));
		return new IssuedChallenge(created.id(), created.createdAt().plus(CODE_LIFETIME));
	}

	private NewChallenge createChallenge(byte[] target) {
		challenges.lockNumber(target);
		// read the clock only once the lock is held: a request that waited must not use a time older than the
		// row it waited for
		Instant now = clock.instant();
		List<Instant> recent = challenges.recentCreatedAt(target, now.minus(LIMIT_WINDOW), MAX_CODES_PER_WINDOW);

		Duration wait = Duration.ZERO;
		if (recent.size() >= MAX_CODES_PER_WINDOW) {
			wait = longer(wait, Duration.between(now, recent.get(MAX_CODES_PER_WINDOW - 1).plus(LIMIT_WINDOW)));
		}
		if (!recent.isEmpty()) {
			wait = longer(wait, Duration.between(now, recent.get(0).plus(RESEND_INTERVAL)));
		}
		if (wait.isPositive()) {
			throw new OtpRateLimitedException(wholeSecondsAtLeastOne(wait));
		}

		UUID id = UUID.randomUUID();
		String code = newCode();
		challenges.insert(id, target, hasher.hashOtpCode(id, code), now, now.plus(CODE_LIFETIME));
		return new NewChallenge(id, code, now);
	}

	/** True if the sender accepted the code in time. Never called inside a transaction. */
	private boolean deliver(MobileNumber mobile, String code) {
		Future<?> sending = SENDERS.submit(() -> sender.send(mobile, code));
		try {
			sending.get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
			return true;
		}
		catch (ExecutionException | TimeoutException ex) {
			sending.cancel(true);
			return false;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			sending.cancel(true);
			return false;
		}
	}

	private static Duration longer(Duration a, Duration b) {
		return a.compareTo(b) >= 0 ? a : b;
	}

	private static Duration wholeSecondsAtLeastOne(Duration wait) {
		long seconds = (wait.toMillis() + 999) / 1000;
		return Duration.ofSeconds(Math.max(1, seconds));
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
