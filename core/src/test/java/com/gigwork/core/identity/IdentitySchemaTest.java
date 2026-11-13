package com.gigwork.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import com.gigwork.core.TestcontainersConfiguration;

/**
 * The guarantees of the identity schema live in the database, not only in code: each test violates one
 * constraint directly with SQL and expects PostgreSQL to refuse it. Every test rolls back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class IdentitySchemaTest {

	private static final Random RANDOM = new Random();

	@Autowired
	JdbcClient jdbc;

	private static byte[] hash() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return bytes;
	}

	private UUID account() {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO identity.account (id) VALUES (:id)").param("id", id).update();
		return id;
	}

	private int mobileIdentity(UUID account, String mobile, byte[] hash) {
		return jdbc.sql("""
				INSERT INTO identity.mobile_identity (account_id, mobile, mobile_hash, verified_at)
				VALUES (:account, :mobile, :hash, now())""")
				.param("account", account).param("mobile", mobile).param("hash", hash).update();
	}

	private int challenge(UUID id, byte[] target, byte[] code, String purpose, int attempts) {
		return jdbc.sql("""
				INSERT INTO identity.otp_challenge (id, target_hash, code_hash, purpose, expires_at, attempts)
				VALUES (:id, :target, :code, :purpose, now() + interval '5 minutes', :attempts)""")
				.param("id", id).param("target", target).param("code", code).param("purpose", purpose)
				.param("attempts", attempts).update();
	}

	private int session(UUID account, byte[] refreshHash, Instant createdAt, Instant lastUsedAt) {
		return jdbc.sql("""
				INSERT INTO identity.session (id, account_id, refresh_token_hash, created_at, last_used_at)
				VALUES (:id, :account, :hash, :created, :used)""")
				.param("id", UUID.randomUUID()).param("account", account).param("hash", refreshHash)
				.param("created", java.sql.Timestamp.from(createdAt)).param("used", java.sql.Timestamp.from(lastUsedAt))
				.update();
	}

	// --- account

	@Test
	void accountDefaultsToActive() {
		UUID id = account();

		assertThat(jdbc.sql("SELECT status FROM identity.account WHERE id = :id").param("id", id)
				.query(String.class).single()).isEqualTo("ACTIVE");
	}

	@Test
	void accountRejectsUnknownStatus() {
		assertThatThrownBy(() -> jdbc.sql("INSERT INTO identity.account (id, status) VALUES (:id, 'BANNED')")
				.param("id", UUID.randomUUID()).update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	// --- mobile identity

	@Test
	void mobileIdentityAcceptsANormalisedIndianNumber() {
		assertThat(mobileIdentity(account(), "+919876543210", hash())).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "9876543210", "919876543210", "+915876543210", "+91987654321", "+9198765432100",
			"+14155550123", "+91 9876543210", "+91987654321a" })
	void mobileIdentityRejectsAnythingButCanonicalIndianNumbers(String mobile) {
		UUID account = account();
		byte[] hash = hash();

		assertThatThrownBy(() -> mobileIdentity(account, mobile, hash))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void mobileIdentityRejectsAHashOfTheWrongLength() {
		UUID account = account();

		assertThatThrownBy(() -> mobileIdentity(account, "+919876543210", new byte[16]))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void twoAccountsCannotShareAMobileHash() {
		byte[] sameHash = hash();
		mobileIdentity(account(), "+919876543210", sameHash);
		UUID other = account();

		assertThatThrownBy(() -> mobileIdentity(other, "+919876543211", sameHash))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void anAccountHasOneMobileIdentity() {
		UUID account = account();
		mobileIdentity(account, "+919876543210", hash());

		assertThatThrownBy(() -> mobileIdentity(account, "+919876543211", hash()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void mobileIdentityNeedsAnExistingAccount() {
		assertThatThrownBy(() -> mobileIdentity(UUID.randomUUID(), "+919876543210", hash()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// --- OTP challenge

	@Test
	void challengeAcceptsUpToFiveAttempts() {
		UUID id = UUID.randomUUID();
		challenge(id, hash(), hash(), "LOGIN", 0);

		for (int attempts = 1; attempts <= 5; attempts++) {
			assertThat(jdbc.sql("UPDATE identity.otp_challenge SET attempts = :a WHERE id = :id")
					.param("a", attempts).param("id", id).update()).isEqualTo(1);
		}
	}

	@Test
	void challengeRejectsASixthAttempt() {
		UUID id = UUID.randomUUID();
		challenge(id, hash(), hash(), "LOGIN", 5);

		assertThatThrownBy(() -> jdbc.sql("UPDATE identity.otp_challenge SET attempts = 6 WHERE id = :id")
				.param("id", id).update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 6, 100 })
	void challengeRejectsOutOfRangeAttempts(int attempts) {
		assertThatThrownBy(() -> challenge(UUID.randomUUID(), hash(), hash(), "LOGIN", attempts))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void challengeRejectsAnUnknownPurpose() {
		assertThatThrownBy(() -> challenge(UUID.randomUUID(), hash(), hash(), "STEP_UP", 0))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void challengeRejectsATargetHashOfTheWrongLength() {
		assertThatThrownBy(() -> challenge(UUID.randomUUID(), new byte[4], hash(), "LOGIN", 0))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void challengeRejectsACodeHashOfTheWrongLength() {
		assertThatThrownBy(() -> challenge(UUID.randomUUID(), hash(), new byte[4], "LOGIN", 0))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void challengeMustExpireAfterItIsCreated() {
		assertThatThrownBy(() -> jdbc.sql("""
				INSERT INTO identity.otp_challenge (id, target_hash, code_hash, purpose, created_at, expires_at)
				VALUES (:id, :t, :c, 'LOGIN', now(), now() - interval '1 minute')""")
				.param("id", UUID.randomUUID()).param("t", hash()).param("c", hash()).update())
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// --- session

	@Test
	void sessionAcceptsAValidRow() {
		Instant now = Instant.now();

		assertThat(session(account(), hash(), now, now)).isEqualTo(1);
	}

	@Test
	void sessionRefreshHashIsUnique() {
		Instant now = Instant.now();
		byte[] sameHash = hash();
		session(account(), sameHash, now, now);
		UUID other = account();

		assertThatThrownBy(() -> session(other, sameHash, now, now))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sessionRejectsARefreshHashOfTheWrongLength() {
		Instant now = Instant.now();
		UUID account = account();

		assertThatThrownBy(() -> session(account, new byte[8], now, now))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sessionCannotBeLastUsedBeforeItWasCreated() {
		Instant now = Instant.now();
		UUID account = account();

		assertThatThrownBy(() -> session(account, hash(), now, now.minusSeconds(60)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sessionNeedsAnExistingAccount() {
		Instant now = Instant.now();

		assertThatThrownBy(() -> session(UUID.randomUUID(), hash(), now, now))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
