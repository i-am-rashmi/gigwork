package com.gigwork.core.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gigwork.core.identity.config.IdentityProperties;
import com.gigwork.core.identity.persistence.SessionRepository;
import com.gigwork.core.identity.persistence.SessionRepository.SessionRef;
import com.gigwork.core.identity.security.AccessTokenIssuer;
import com.gigwork.core.identity.security.AccessTokenIssuer.IssuedAccessToken;
import com.gigwork.core.identity.security.RefreshTokens;

/**
 * Sessions and their tokens: a 15-minute access token plus a refresh token that is replaced on every use, with a
 * 7-day idle and a 30-day absolute limit (NFR-31, ADR-0005). Reuse detection is not built yet.
 */
@Service
public class SessionService {

	private final SessionRepository sessions;
	private final AccessTokenIssuer accessTokens;
	private final IdentityProperties properties;
	private final Clock clock;

	SessionService(SessionRepository sessions, AccessTokenIssuer accessTokens, IdentityProperties properties,
			Clock clock) {
		this.sessions = sessions;
		this.accessTokens = accessTokens;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public AuthTokens start(UUID accountId) {
		Instant now = clock.instant();
		UUID sessionId = UUID.randomUUID();
		String refreshToken = RefreshTokens.generate();

		sessions.insert(sessionId, accountId, RefreshTokens.hash(refreshToken), now);
		return tokens(accountId, sessionId, refreshToken, now);
	}

	/**
	 * Exchanges a refresh token for a new pair. Empty (so 401) for every failure: malformed, unknown, already
	 * used, revoked, idle too long, too old, or account not active.
	 */
	@Transactional
	public Optional<AuthTokens> refresh(String refreshToken) {
		if (!RefreshTokens.hasValidFormat(refreshToken)) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		String next = RefreshTokens.generate();
		Optional<SessionRef> session = sessions.rotate(RefreshTokens.hash(refreshToken), RefreshTokens.hash(next), now,
				properties.session().idleTimeout(), properties.session().absoluteTimeout());
		return session.map(s -> tokens(s.accountId(), s.sessionId(), next, now));
	}

	private AuthTokens tokens(UUID accountId, UUID sessionId, String refreshToken, Instant now) {
		IssuedAccessToken access = accessTokens.issue(accountId, sessionId, now);
		return new AuthTokens(access.token(), properties.jwt().accessTokenTtl().toSeconds(), refreshToken);
	}

}
