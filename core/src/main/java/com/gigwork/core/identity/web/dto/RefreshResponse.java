package com.gigwork.core.identity.web.dto;

import com.gigwork.core.identity.application.AuthTokens;

/** A new token pair; the refresh token that was sent no longer works. */
public record RefreshResponse(String accessToken, String tokenType, long expiresIn, String refreshToken) {

	public static RefreshResponse of(AuthTokens tokens) {
		return new RefreshResponse(tokens.accessToken(), "Bearer", tokens.expiresInSeconds(), tokens.refreshToken());
	}

	/** Never print tokens (NFR-46). */
	@Override
	public String toString() {
		return "RefreshResponse[***]";
	}

}
