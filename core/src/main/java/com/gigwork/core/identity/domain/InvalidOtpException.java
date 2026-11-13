package com.gigwork.core.identity.domain;

/**
 * The code is wrong, expired, already used, out of attempts, or does not belong to this number. One exception for
 * all of them so a caller learns nothing about which case it was.
 */
public class InvalidOtpException extends RuntimeException {

	public InvalidOtpException() {
		super("Invalid or expired code");
	}

}
