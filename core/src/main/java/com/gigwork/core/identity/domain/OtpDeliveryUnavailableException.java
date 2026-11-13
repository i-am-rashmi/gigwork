package com.gigwork.core.identity.domain;

/**
 * No OTP sender is configured, or sending failed. The code was not delivered: its challenge is kept but marked
 * as failed (it cannot be used and still counts toward the per-number limit); the previous code stays valid.
 */
public class OtpDeliveryUnavailableException extends RuntimeException {

	public OtpDeliveryUnavailableException() {
		super("The one-time code could not be delivered");
	}

}
