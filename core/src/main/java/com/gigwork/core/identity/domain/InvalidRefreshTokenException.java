package com.gigwork.core.identity.domain;

/** The refresh token is unknown, used, revoked or expired. One exception for all, so a caller learns nothing more. */
public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("Invalid refresh token");
	}

}
