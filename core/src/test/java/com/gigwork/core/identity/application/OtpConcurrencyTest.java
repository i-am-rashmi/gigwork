package com.gigwork.core.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.gigwork.core.identity.application.AuthenticationService.LoginResult;
import com.gigwork.core.identity.application.OtpService.IssuedChallenge;
import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.persistence.OtpChallengeRepository;
import com.gigwork.core.identity.security.KeyedHasher;
import com.gigwork.core.identity.support.CapturingOtpSender;
import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.TestNumbers;

/**
 * The invariants of the OTP flow under simultaneous requests: "5 attempts" really means 5, a code works once,
 * and one number never ends up with two accounts. They are enforced by single SQL statements and constraints,
 * so these tests start many real threads at the same moment against real PostgreSQL.
 */
@IdentityIntegrationTest
class OtpConcurrencyTest {

	private static final int THREADS = 20;

	@Autowired
	OtpService otp;
	@Autowired
	AuthenticationService authentication;
	@Autowired
	OtpChallengeRepository challenges;
	@Autowired
	CapturingOtpSender sender;
	@Autowired
	KeyedHasher hasher;
	@Autowired
	JdbcClient jdbc;

	private int attempts(UUID challenge) {
		return jdbc.sql("SELECT attempts FROM identity.otp_challenge WHERE id = :id").param("id", challenge)
				.query(Integer.class).single();
	}

	@Test
	void twentySimultaneousWrongGuessesUseUpExactlyFiveAttempts() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		IssuedChallenge challenge = otp.request(mobile);
		String code = sender.lastCode(mobile);
		String wrong = code.equals("000000") ? "000001" : "000000";

		List<Optional<LoginResult>> results = Concurrently.runAll(THREADS,
				() -> authentication.verifyOtp(mobile, challenge.challengeId(), wrong));

		assertThat(results).allMatch(Optional::isEmpty);
		assertThat(attempts(challenge.challengeId())).as("no more than 5 guesses were ever evaluated").isEqualTo(5);
		assertThat(authentication.verifyOtp(mobile, challenge.challengeId(), code))
				.as("the right code no longer works once attempts are used up").isEmpty();
	}

	@Test
	void simultaneousSubmissionsOfTheRightCodeSucceedExactlyOnce() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		IssuedChallenge challenge = otp.request(mobile);
		String code = sender.lastCode(mobile);

		List<Optional<LoginResult>> results = Concurrently.runAll(THREADS,
				() -> authentication.verifyOtp(mobile, challenge.challengeId(), code));

		assertThat(results.stream().filter(Optional::isPresent)).hasSize(1);
		assertThat(accountsFor(mobile)).isEqualTo(1);
	}

	/**
	 * Several live challenges for one new number, all verified at the same moment (more than one can be live if
	 * requests overlap). The unique mobile_hash must give exactly one account, and every login must land on it.
	 */
	@RepeatedTest(5)
	void simultaneousFirstLoginsOfOneNewNumberCreateExactlyOneAccount() throws Exception {
		MobileNumber mobile = TestNumbers.next();
		byte[] target = hasher.hashMobile(mobile);
		Instant now = Instant.now();
		List<Callable<Optional<LoginResult>>> logins = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			UUID id = UUID.randomUUID();
			String code = String.format("%06d", i);
			challenges.insert(id, target, hasher.hashOtpCode(id, code), now, now.plusSeconds(300));
			logins.add(() -> authentication.verifyOtp(mobile, id, code));
		}

		List<Optional<LoginResult>> results = Concurrently.runAll(logins);

		assertThat(results).allMatch(Optional::isPresent);
		assertThat(results.stream().map(r -> r.get().accountId()).distinct()).hasSize(1);
		assertThat(results.stream().filter(r -> r.get().newAccount())).as("exactly one call created it").hasSize(1);
		assertThat(accountsFor(mobile)).isEqualTo(1);
		assertThat(jdbc.sql("SELECT count(*) FROM identity.account a WHERE NOT EXISTS (SELECT 1 FROM identity.mobile_identity m WHERE m.account_id = a.id)")
				.query(Long.class).single()).as("no orphan accounts left by lost races").isZero();
	}

	private long accountsFor(MobileNumber mobile) {
		return jdbc.sql("SELECT count(*) FROM identity.mobile_identity WHERE mobile_hash = :hash")
				.param("hash", hasher.hashMobile(mobile)).query(Long.class).single();
	}

}
