package com.gigwork.core.identity.persistence;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Login sessions. The refresh token is stored only as a hash and replaced on every use (ADR-0005). */
@Repository
public class SessionRepository {

	public record SessionRef(UUID sessionId, UUID accountId) {
	}

	private final SessionJpaRepository sessions;
	private final JdbcClient jdbc;

	SessionRepository(SessionJpaRepository sessions, JdbcClient jdbc) {
		this.sessions = sessions;
		this.jdbc = jdbc;
	}

	public void insert(UUID sessionId, UUID accountId, byte[] refreshTokenHash, Instant now) {
		sessions.save(new Session(sessionId, accountId, refreshTokenHash, now));
	}

	/**
	 * Replaces the refresh token of the session that holds {@code oldHash}, but only if the session is not
	 * revoked, was used within {@code idleTimeout}, is younger than {@code absoluteTimeout}, and belongs to an
	 * active account. One statement: of any number of simultaneous refreshes with the same token, exactly one
	 * finds the old hash still in place (the others wait for its row lock, then no longer match).
	 */
	public Optional<SessionRef> rotate(byte[] oldHash, byte[] newHash, Instant now, Duration idleTimeout,
			Duration absoluteTimeout) {
		sessions.flush(); // JdbcClient bypasses the persistence context: write pending inserts first
		return jdbc.sql("""
				UPDATE identity.session s SET refresh_token_hash = :newHash, last_used_at = :now
				FROM identity.account a
				WHERE s.refresh_token_hash = :oldHash
				  AND s.revoked_at IS NULL
				  AND s.last_used_at > :idleCutoff
				  AND s.created_at > :absoluteCutoff
				  AND a.id = s.account_id AND a.status = 'ACTIVE'
				RETURNING s.id, s.account_id""")
				.param("oldHash", oldHash).param("newHash", newHash).param("now", utc(now))
				.param("idleCutoff", utc(now.minus(idleTimeout)))
				.param("absoluteCutoff", utc(now.minus(absoluteTimeout)))
				.query((rs, i) -> new SessionRef(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class)))
				.optional();
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
