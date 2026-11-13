package com.gigwork.core.identity.otp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.domain.OtpDeliveryUnavailableException;

/**
 * The default sender: delivers nothing and says so. Failing loudly (503) beats silently dropping the code, so a
 * deployment without a real sender cannot look as if it works.
 */
@Component
@ConditionalOnProperty(name = "gigwork.identity.otp.sender", havingValue = "none", matchIfMissing = true)
class UnavailableOtpSender implements OtpSender {

	@Override
	public void send(MobileNumber mobile, String code) {
		throw new OtpDeliveryUnavailableException();
	}

}
