package com.gigwork.core.platform;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The account behind the current request, taken from the access token's {@code sub} claim (the account ID). The
 * signature has already been checked by the security configuration, so modules never parse tokens themselves.
 */
public final class CurrentAccount {

	private CurrentAccount() {
	}

	/** The caller's account ID, or empty on a public endpoint called without a token (a visitor). */
	public static Optional<UUID> optional() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken token && authentication.isAuthenticated()) {
			String subject = token.getToken().getSubject();
			if (subject != null) {
				try {
					return Optional.of(UUID.fromString(subject));
				}
				catch (IllegalArgumentException e) {
					return Optional.empty();
				}
			}
		}
		return Optional.empty();
	}

	/** The caller's account ID on an endpoint that requires a token. */
	public static UUID required() {
		return optional().orElseThrow(() -> new AuthenticationCredentialsNotFoundException("no authenticated account"));
	}

}
