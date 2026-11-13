package com.gigwork.core.identity.domain;

import java.time.Duration;

/**
 * Too many codes requested for one number (NFR-28): at most 3 per 15 minutes, and 30 seconds between codes.
 * The message never contains the number (NFR-46).
 */
public class OtpRateLimitedException extends RuntimeException {

	private final Duration retryAfter;

	public OtpRateLimitedException(Duration retryAfter) {
		super("Too many one-time code requests");
		this.retryAfter = retryAfter;
	}

	/** How long until a new code may be requested, at least one second. */
	public Duration retryAfter() {
		return retryAfter;
	}

}
