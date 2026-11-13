package com.gigwork.core.identity.otp;

import com.gigwork.core.identity.domain.MobileNumber;

/**
 * Delivers a one-time code to a mobile number (ADR-0005). Exactly one implementation exists in the application,
 * chosen by {@code gigwork.identity.otp.sender}. A real SMS provider arrives with DLT registration.
 */
public interface OtpSender {

	/** @throws com.gigwork.core.identity.domain.OtpDeliveryUnavailableException if the code cannot be delivered */
	void send(MobileNumber mobile, String code);

}
