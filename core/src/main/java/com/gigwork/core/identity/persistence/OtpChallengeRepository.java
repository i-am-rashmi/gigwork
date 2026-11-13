package com.gigwork.core.identity.persistence;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * OTP challenges. The rules that must hold under simultaneous requests are single SQL statements, so PostgreSQL
 * enforces them (row lock plus a re-checked WHERE clause), not check-then-act code (ADR-0002):
 * <ul>
 * <li>{@link #recordAttempt}: counts an attempt only while the challenge is live and has fewer than 5, so five
 * attempts really means five even with many requests at once (NFR-37);</li>
 * <li>{@link #markUsed}: lets exactly one caller consume a challenge.</li>
 * </ul>
 */
@Repository
public class OtpChallengeRepository {

	/** NFR-37. Also enforced by the otp_challenge_attempts_range constraint. */
	public static final int MAX_ATTEMPTS = 5;

	private final OtpChallengeJpaRepository challenges;
	private final JdbcClient jdbc;

	OtpChallengeRepository(OtpChallengeJpaRepository challenges, JdbcClient jdbc) {
		this.challenges = challenges;
		this.jdbc = jdbc;
	}

	public void insert(UUID id, byte[] targetHash, byte[] codeHash, Instant createdAt, Instant expiresAt) {
		challenges.save(new OtpChallenge(id, targetHash, codeHash, createdAt, expiresAt));
	}

	/**
	 * Takes a transaction-scoped advisory lock derived from the number's hash, so requests for one number are
	 * serialised from here until commit while other numbers are unaffected. Must run inside a transaction. Two
	 * numbers whose hashes share the first 8 bytes would only wait for each other needlessly.
	 */
	public void lockNumber(byte[] targetHash) {
		long key = ByteBuffer.wrap(targetHash, 0, Long.BYTES).getLong();
		jdbc.sql("SELECT pg_advisory_xact_lock(:key)").param("key", key).query().singleValue();
	}

	/**
	 * Creation times of the newest (up to {@code limit}) challenges of this number created after {@code since},
	 * newest first. Failed deliveries count: they are rows like any other.
	 */
	public List<Instant> recentCreatedAt(byte[] targetHash, Instant since, int limit) {
		challenges.flush();
		return jdbc.sql("""
				SELECT created_at FROM identity.otp_challenge
				WHERE target_hash = :target AND created_at > :since
				ORDER BY created_at DESC LIMIT :limit""")
				.param("target", targetHash).param("since", utc(since)).param("limit", limit)
				.query(OffsetDateTime.class).list().stream().map(OffsetDateTime::toInstant).toList();
	}

	/** Ends the live challenges of this number that are older than the new one, so only the newest code works. */
	public int supersedeOlder(byte[] targetHash, Instant newCreatedAt, Instant now) {
		return challenges.supersedeOlder(targetHash, newCreatedAt, now);
	}

	/** The code could not be delivered: the challenge can never be verified, but still counts toward the limit. */
	public void markDeliveryFailed(UUID id, Instant now) {
		challenges.markDeliveryFailed(id, now);
	}

	/**
	 * Counts one attempt and returns the stored code hash, but only if the challenge exists for this number, is
	 * unused and unexpired, and has attempts left. Empty means "do not even compare".
	 */
	public Optional<byte[]> recordAttempt(UUID id, byte[] targetHash, Instant now) {
		challenges.flush(); // JdbcClient bypasses the persistence context: write pending inserts first
		return jdbc.sql("""
				UPDATE identity.otp_challenge SET attempts = attempts + 1
				WHERE id = :id AND target_hash = :target AND purpose = 'LOGIN'
				  AND used_at IS NULL AND delivery_failed_at IS NULL AND expires_at > :now AND attempts < :max
				RETURNING code_hash""")
				.param("id", id).param("target", targetHash).param("now", utc(now)).param("max", MAX_ATTEMPTS)
				.query(byte[].class).optional();
	}

	/**
	 * True for exactly one caller: the one that finds the challenge still unused. {@link #recordAttempt} already
	 * serialises callers (its row lock lasts until commit and its WHERE clause is re-checked), so this guard is a
	 * second layer: it keeps the single-use rule true even if the two are ever called in separate transactions.
	 * OtpConcurrencyTest fails if both are removed.
	 */
	public boolean markUsed(UUID id, Instant now) {
		return challenges.markUsed(id, now) == 1;
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
