package com.gigwork.core.identity.application;

/**
 * @param accessToken      signed JWT, valid for {@code expiresInSeconds}
 * @param expiresInSeconds lifetime of the access token
 * @param refreshToken     opaque, single-use: replaced by the next refresh
 */
public record AuthTokens(String accessToken, long expiresInSeconds, String refreshToken) {

	/** Never print tokens (NFR-46). */
	@Override
	public String toString() {
		return "AuthTokens[expiresInSeconds=" + expiresInSeconds + "]";
	}

}
