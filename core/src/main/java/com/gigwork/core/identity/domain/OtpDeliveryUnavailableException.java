package com.gigwork.core.identity.domain;

/** No OTP sender is configured, or sending failed. The code was not delivered, so no challenge is kept. */
public class OtpDeliveryUnavailableException extends RuntimeException {

	public OtpDeliveryUnavailableException() {
		super("The one-time code could not be delivered");
	}

}
