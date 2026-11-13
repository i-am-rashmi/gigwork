package com.gigwork.core.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.gigwork.core.identity.application.AccountService.VerifiedAccount;
import com.gigwork.core.identity.application.OtpService.IssuedChallenge;
import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.security.KeyedHasher;
import com.gigwork.core.identity.support.CapturingOtpSender;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestNumbers;

/** Sign up and log in with a code (FR-1, FR-3), and the OTP rules of NFR-37, against real PostgreSQL. */
@IdentityIntegrationTest
class OtpVerificationTest {

	@Autowired
	OtpService otp;
	@Autowired
	AuthenticationService authentication;
	@Autowired
	CapturingOtpSender sender;
	@Autowired
	MutableClock clock;
	@Autowired
	KeyedHasher hasher;
	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void resetClock() {
		clock.reset();
	}

	private IssuedChallenge issue(MobileNumber mobile) {
		return otp.request(mobile);
	}

	private static String wrong(String code) {
		return code.equals("000000") ? "000001" : "000000";
	}

	private int attempts(UUID challenge) {
		return jdbc.sql("SELECT attempts FROM identity.otp_challenge WHERE id = :id").param("id", challenge)
				.query(Integer.class).single();
	}

	private boolean isUsed(UUID challenge) {
		return jdbc.sql("SELECT used_at IS NOT NULL FROM identity.otp_challenge WHERE id = :id").param("id", challenge)
				.query(Boolean.class).single();
	}

	private long accountsFor(MobileNumber mobile) {
		return jdbc.sql("SELECT count(*) FROM identity.mobile_identity WHERE mobile_hash = :hash")
				.param("hash", hasher.hashMobile(mobile)).query(Long.class).single();
	}

	// --- sign up and log in

	@Test
	void aNewNumberSignsUpAndIsVerified() {
		MobileNumber mobile = TestNumbers.next();
		IssuedChallenge challenge = issue(mobile);

		VerifiedAccount account = authentication.verifyOtp(mobile, challenge.challengeId(), sender.lastCode(mobile))
				.orElseThrow();

		assertThat(account.newAccount()).isTrue();
		var row = jdbc.sql("""
				SELECT a.status, m.mobile, m.mobile_hash, m.verified_at
				FROM identity.account a JOIN identity.mobile_identity m ON m.account_id = a.id
				WHERE a.id = :id""").param("id", account.accountId())
				.query((rs, i) -> new Object[] { rs.getString("status"), rs.getString("mobile"),
						rs.getBytes("mobile_hash"), rs.getObject("verified_at", OffsetDateTime.class) })
				.single();
		assertThat(row[0]).isEqualTo("ACTIVE");
		assertThat(row[1]).as("plain for now, see TODO(NFR-44)").isEqualTo(mobile.value());
		assertThat((byte[]) row[2]).isEqualTo(hasher.hashMobile(mobile));
		assertThat((OffsetDateTime) row[3]).isNotNull();
		assertThat(isUsed(challenge.challengeId())).isTrue();
	}

	@Test
	void aReturningNumberLogsInToTheSameAccount() {
		MobileNumber mobile = TestNumbers.next();
		IssuedChallenge first = issue(mobile);
		VerifiedAccount signedUp = authentication.verifyOtp(mobile, first.challengeId(), sender.lastCode(mobile))
				.orElseThrow();

		IssuedChallenge second = issue(mobile);
		VerifiedAccount loggedIn = authentication.verifyOtp(mobile, second.challengeId(), sender.lastCode(mobile))
				.orElseThrow();

		assertThat(loggedIn.accountId()).isEqualTo(signedUp.accountId());
		assertThat(loggedIn.newAccount()).isFalse();
		assertThat(accountsFor(mobile)).isEqualTo(1);
	}

	// --- failures all look the same: empty

	@Test
	void aWrongCodeIsRejectedAndCounted() {
		MobileNumber mobile = TestNumbers.next();
		IssuedChallenge challenge = issue(mobile);

		assertThat(authentication.verifyOtp(mobile, challenge.challengeId(), wrong(sender.lastCode(mobile)))).isEmpty();

		assertThat(attempts(challenge.challengeId())).isEqualTo(1);
		assertThat(isUsed(challenge.challengeId())).isFalse();
		assertThat(accountsFor(mobile)).isZero();
	}

	@Test
	void afterFiveWrongAttemptsEvenTheRightCodeIsRejected() {
		MobileNumber mobile = TestNumbers.next();
		IssuedChallenge challenge = issue(mobile);
		String code = sender.lastCode(mobile);

		for (int i = 0; i < 5; i++) {
			assertThat(authentication.verifyOtp(mobile, challenge.challengeId(), wrong(code))).isEmpty();
		}

		assertThat(authentication.verifyOtp(mobile, challenge.challengeId(), code)).isEmpty();
		assertThat(attempts(challenge.challengeId())).isEqualTo(5);
		assertThat(accountsFor(mobile)).isZero();
	}

	@Test
	void theFifthAttemptStillCounts() {
		MobileNumber mobile = TestNumbers.next();
		IssuedChallenge challenge = issue(mobile);
		String code = sender.lastCode(mobile);

		for (int i = 0; i < 4; i++) {
			assertThat(authentication.verifyOtp(mobile, challenge.challengeId(), wrong(code))).isEmpty();
		}

		assertThat(authentication.verifyOtp(mobile, challenge.challengeId(), code)).isPresent();
		assertThat(attempts(challenge.challengeId())).isEqualTo(5);
	}

	@Test
	void aCodeExpiresAfterFiveMinutes() {
		MobileNumber mobile = TestNumbers.next();
		IssuedChallenge challenge = issue(mobile);
		clock.advance(Duration.ofMinutes(5));

		assertThat(authentication.verifyOtp(mobile, challenge.challengeId(), sender.lastCode(mobile))).isEmpty();
		assertThat(accountsFor(mobile)).isZero();
	}

	@Test
	void aCodeStillWorksJustBeforeItExpires() {
		MobileNumber mobile = TestNumbers.next();
		IssuedChallenge challenge = issue(mobile);
		clock.advance(Duration.ofMinutes(5).minusSeconds(1));

		assertThat(authentication.verifyOtp(mobile, challenge.challengeId(), sender.lastCode(mobile))).isPresent();
	}

	@Test
	void aCodeWorksOnlyOnce() {
		MobileNumber mobile = TestNumbers.next();
		IssuedChallenge challenge = issue(mobile);
		String code = sender.lastCode(mobile);

		assertThat(authentication.verifyOtp(mobile, challenge.challengeId(), code)).isPresent();
		assertThat(authentication.verifyOtp(mobile, challenge.challengeId(), code)).isEmpty();
	}

	@Test
	void aCodeIsBoundToItsNumber() {
		MobileNumber owner = TestNumbers.next();
		MobileNumber other = TestNumbers.next();
		IssuedChallenge challenge = issue(owner);
		String code = sender.lastCode(owner);

		assertThat(authentication.verifyOtp(other, challenge.challengeId(), code)).isEmpty();

		assertThat(accountsFor(other)).isZero();
		assertThat(attempts(challenge.challengeId())).as("a stranger cannot burn the owner's attempts").isZero();
		assertThat(authentication.verifyOtp(owner, challenge.challengeId(), code)).isPresent();
	}

	@Test
	void anUnknownChallengeIsRejected() {
		MobileNumber mobile = TestNumbers.next();
		issue(mobile);

		assertThat(authentication.verifyOtp(mobile, UUID.randomUUID(), sender.lastCode(mobile))).isEmpty();
	}

	@Test
	void onlyTheNewestCodeOfANumberWorks() {
		MobileNumber mobile = TestNumbers.next();
		IssuedChallenge first = issue(mobile);
		String firstCode = sender.lastCode(mobile);
		issue(mobile);

		assertThat(authentication.verifyOtp(mobile, first.challengeId(), firstCode)).isEmpty();
	}

	@Test
	void anAccountThatIsNotActiveCannotLogIn() {
		MobileNumber mobile = TestNumbers.next();
		UUID accountId = UUID.randomUUID();
		jdbc.sql("INSERT INTO identity.account (id, status) VALUES (:id, 'DELETION_REQUESTED')").param("id", accountId)
				.update();
		jdbc.sql("""
				INSERT INTO identity.mobile_identity (account_id, mobile, mobile_hash, verified_at)
				VALUES (:id, :mobile, :hash, now())""")
				.param("id", accountId).param("mobile", mobile.value()).param("hash", hasher.hashMobile(mobile))
				.update();
		IssuedChallenge challenge = issue(mobile);

		assertThat(authentication.verifyOtp(mobile, challenge.challengeId(), sender.lastCode(mobile))).isEmpty();
	}

}
