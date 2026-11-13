package com.gigwork.core.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.identity.application.OtpService.IssuedChallenge;
import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.domain.OtpDeliveryUnavailableException;
import com.gigwork.core.identity.domain.OtpRateLimitedException;
import com.gigwork.core.identity.persistence.OtpChallengeRepository;
import com.gigwork.core.identity.security.KeyedHasher;
import com.gigwork.core.identity.support.CapturingOtpSender;
import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestNumbers;

/**
 * NFR-28: at most 3 codes per 15 minutes per number, and a new code only 30 seconds after the previous one,
 * counted from otp_challenge rows (failed deliveries included) and exact under simultaneous requests: a
 * transaction-scoped advisory lock per number serialises the count and the insert. These tests start many real
 * threads at the same moment against real PostgreSQL; the clock only moves when a test moves it.
 */
@IdentityIntegrationTest
class OtpRateLimitTest {

	private static final Duration INTERVAL = OtpService.RESEND_INTERVAL.plusSeconds(1);

	@Autowired
	OtpService otp;
	@Autowired
	AuthenticationService authentication;
	@Autowired
	OtpChallengeRepository challenges;
	@Autowired
	CapturingOtpSender sender;
	@Autowired
	MutableClock clock;
	@Autowired
	KeyedHasher hasher;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	PlatformTransactionManager transactions;

	@BeforeEach
	void resetClockAndSender() {
		clock.reset();
		sender.reset();
	}

	@AfterEach
	void resetSender() {
		sender.reset();
	}

	private Optional<IssuedChallenge> tryRequest(MobileNumber mobile) {
		try {
			return Optional.of(otp.request(mobile));
		}
		catch (OtpRateLimitedException ex) {
			return Optional.empty();
		}
	}

	private long rows(MobileNumber mobile) {
		return jdbc.sql("SELECT count(*) FROM identity.otp_challenge WHERE target_hash = :hash")
				.param("hash", hasher.hashMobile(mobile)).query(Long.class).single();
	}

	private static long accepted(List<Optional<IssuedChallenge>> results) {
		return results.stream().filter(Optional::isPresent).count();
	}

	// --- exactness under concurrency ---------------------------------------------------------------------

	@Test
	void twentySimultaneousRequestsForOneNumberLetExactlyOneThrough() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		int sentBefore = sender.sentCount();

		List<Optional<IssuedChallenge>> results = Concurrently.runAll(20, () -> tryRequest(mobile));

		// all 20 fall into the same 30 seconds, so only the first may be accepted
		assertThat(accepted(results)).isEqualTo(1);
		assertThat(rows(mobile)).isEqualTo(1);
		assertThat(sender.sentCount() - sentBefore).as("rejected requests never reach the sender").isEqualTo(1);
	}

	/** Two older codes exist already: the window count (not only the 30 second rule) must hold under the lock. */
	@Test
	void simultaneousRequestsNeverExceedThreePerWindow() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		byte[] target = hasher.hashMobile(mobile);
		Instant now = clock.instant();
		for (int minutesAgo : new int[] { 10, 5 }) {
			UUID id = UUID.randomUUID();
			Instant created = now.minus(Duration.ofMinutes(minutesAgo));
			challenges.insert(id, target, hasher.hashOtpCode(id, "123456"), created, created.plus(OtpService.CODE_LIFETIME));
		}

		List<Optional<IssuedChallenge>> results = Concurrently.runAll(20, () -> tryRequest(mobile));

		assertThat(accepted(results)).isEqualTo(1);
		assertThat(rows(mobile)).as("2 seeded + exactly 1 new").isEqualTo(3);
	}

	@Test
	void repeatedBurstsAcrossTheWindowAcceptExactlyThreeCodes() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		long accepted = 0;

		for (int round = 0; round < 6; round++) {
			accepted += accepted(Concurrently.runAll(10, () -> tryRequest(mobile)));
			clock.advance(INTERVAL);
		}

		assertThat(accepted).isEqualTo(3);
		assertThat(rows(mobile)).isEqualTo(3);
	}

	@Test
	void differentNumbersDoNotBlockEachOther() throws Exception {
		List<Callable<Optional<IssuedChallenge>>> requests = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			MobileNumber mobile = TestNumbers.next();
			requests.add(() -> tryRequest(mobile));
		}

		List<Optional<IssuedChallenge>> results = Concurrently.runAll(requests);

		assertThat(accepted(results)).isEqualTo(20);
	}

	@Test
	void aNumberAtItsLimitDoesNotAffectAnotherNumber() {
		MobileNumber limited = TestNumbers.next();
		MobileNumber other = TestNumbers.next();
		for (int i = 0; i < 3; i++) {
			otp.request(limited);
			clock.advance(INTERVAL);
		}
		assertThatThrownBy(() -> otp.request(limited)).isInstanceOf(OtpRateLimitedException.class);

		assertThat(tryRequest(other)).isPresent();
	}

	// --- the rules --------------------------------------------------------------------------------------

	@Test
	void aNewCodeIsAllowedOnlyThirtySecondsAfterThePreviousOne() {
		MobileNumber mobile = TestNumbers.next();
		otp.request(mobile);

		clock.advance(Duration.ofSeconds(29));
		assertThatThrownBy(() -> otp.request(mobile)).isInstanceOfSatisfying(OtpRateLimitedException.class,
				ex -> assertThat(ex.retryAfter()).isEqualTo(Duration.ofSeconds(1)));

		clock.advance(Duration.ofSeconds(1));
		assertThat(tryRequest(mobile)).isPresent();
	}

	/**
	 * PostgreSQL stores microseconds and rounds; a clock with nanoseconds made "newest + 30 s - now" slightly
	 * positive at exactly 30 s (seen on Linux, whose clock has nanosecond precision). The clock gives whole
	 * microseconds, so exactly 30 seconds later is always allowed.
	 */
	@Test
	void exactlyThirtySecondsLaterIsAllowedEvenWhenTheClockHasNanoseconds() {
		MobileNumber mobile = TestNumbers.next();
		clock.set(Instant.parse("2026-01-01T10:00:00.123456789Z"));
		otp.request(mobile);

		clock.advance(Duration.ofSeconds(30));

		assertThat(tryRequest(mobile)).isPresent();
	}

	@Test
	void theFourthCodeWithinFifteenMinutesWaitsUntilTheFirstLeavesTheWindow() {
		MobileNumber mobile = TestNumbers.next();
		for (int i = 0; i < 3; i++) {
			otp.request(mobile);
			clock.advance(INTERVAL);
		}
		// codes were sent at +0, +31 and +62 seconds; now is +93 seconds
		assertThatThrownBy(() -> otp.request(mobile)).isInstanceOfSatisfying(OtpRateLimitedException.class,
				ex -> assertThat(ex.retryAfter()).isEqualTo(Duration.ofSeconds(15 * 60 - 93)));

		clock.advance(Duration.ofSeconds(15 * 60 - 93 - 1));
		assertThatThrownBy(() -> otp.request(mobile)).isInstanceOfSatisfying(OtpRateLimitedException.class,
				ex -> assertThat(ex.retryAfter()).isEqualTo(Duration.ofSeconds(1)));

		clock.advance(Duration.ofSeconds(1));
		assertThat(tryRequest(mobile)).isPresent();
	}

	// --- failed deliveries ------------------------------------------------------------------------------

	@Test
	void aFailedSendLeavesThePreviousCodeValidAndTheFailedOneUnusable() {
		MobileNumber mobile = TestNumbers.next();
		IssuedChallenge previous = otp.request(mobile);
		String previousCode = sender.lastCode(mobile);
		clock.advance(INTERVAL);

		sender.failSends(true);
		assertThatThrownBy(() -> otp.request(mobile)).isInstanceOf(OtpDeliveryUnavailableException.class);
		String failedCode = sender.lastCode(mobile);
		UUID failedId = jdbc.sql("SELECT id FROM identity.otp_challenge WHERE target_hash = :hash AND delivery_failed_at IS NOT NULL")
				.param("hash", hasher.hashMobile(mobile)).query(UUID.class).single();

		assertThat(failedCode).isNotEqualTo(previousCode);
		assertThat(authentication.verifyOtp(mobile, failedId, failedCode)).as("the undelivered code cannot be used").isEmpty();
		assertThat(authentication.verifyOtp(mobile, previous.challengeId(), previousCode))
				.as("the previous code was never superseded").isPresent();
	}

	@Test
	void failedSendsCountTowardTheLimit() {
		MobileNumber mobile = TestNumbers.next();
		sender.failSends(true);
		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> otp.request(mobile)).isInstanceOf(OtpDeliveryUnavailableException.class);
			clock.advance(INTERVAL);
		}
		sender.failSends(false);
		int sentBefore = sender.sentCount();

		assertThatThrownBy(() -> otp.request(mobile)).isInstanceOf(OtpRateLimitedException.class);

		assertThat(sender.sentCount()).as("a limited request is not even sent").isEqualTo(sentBefore);
		assertThat(rows(mobile)).isEqualTo(3);
	}

	@Test
	void aSuccessfulRequestEndsOnlyOlderCodes() {
		MobileNumber mobile = TestNumbers.next();
		IssuedChallenge first = otp.request(mobile);
		clock.advance(INTERVAL);
		IssuedChallenge second = otp.request(mobile);

		assertThat(authentication.verifyOtp(mobile, first.challengeId(), sender.lastCode(mobile))).isEmpty();
		assertThat(authentication.verifyOtp(mobile, second.challengeId(), sender.lastCode(mobile))).isPresent();
	}

	// --- no network call inside a transaction -----------------------------------------------------------

	/**
	 * The sender runs on its own thread, so a thread-local check would prove nothing. Instead: a transaction
	 * that holds the number's advisory lock would show up in pg_locks while the sender runs.
	 */
	@Test
	void noTransactionIsOpenWhileTheCodeIsBeingSent() {
		long advisoryLocks = advisoryLocks();
		TransactionTemplate tx = new TransactionTemplate(transactions);
		long seenInsideTransaction = tx.execute(status -> {
			challenges.lockNumber(hasher.hashMobile(TestNumbers.next()));
			return advisoryLocks();
		});
		assertThat(seenInsideTransaction).as("the check can see a held lock").isGreaterThan(advisoryLocks);

		MobileNumber mobile = TestNumbers.next();
		AtomicInteger locksDuringSend = new AtomicInteger(-1);
		sender.onSend(m -> locksDuringSend.set((int) advisoryLocks()));

		otp.request(mobile);

		assertThat(locksDuringSend.get()).as("no lock (so no open transaction) while sending").isEqualTo(advisoryLocks);
	}

	private long advisoryLocks() {
		return jdbc.sql("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory'").query(Long.class).single();
	}

}
