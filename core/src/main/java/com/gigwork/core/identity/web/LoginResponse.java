package com.gigwork.core.identity.web;

import com.gigwork.core.identity.application.AuthTokens;

/**
 * @param accessToken  send as {@code Authorization: Bearer <accessToken>}
 * @param tokenType    always {@code Bearer}
 * @param expiresIn    seconds until the access token expires
 * @param refreshToken use once at {@code /v1/auth/token/refresh} to get a new pair
 * @param newAccount   true if this login created the account
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
		boolean newAccount) {

	static LoginResponse of(AuthTokens tokens, boolean newAccount) {
		return new LoginResponse(tokens.accessToken(), "Bearer", tokens.expiresInSeconds(), tokens.refreshToken(),
				newAccount);
	}

	/** Never print tokens (NFR-46). */
	@Override
	public String toString() {
		return "LoginResponse[newAccount=" + newAccount + "]";
	}

}
