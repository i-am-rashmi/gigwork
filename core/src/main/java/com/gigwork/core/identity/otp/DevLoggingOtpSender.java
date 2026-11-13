package com.gigwork.core.identity.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.gigwork.core.identity.domain.MobileNumber;

/**
 * Development only (ADR-0005): writes the code to the log so a developer can sign in without an SMS provider.
 * It exists only when {@code gigwork.identity.otp.sender=dev}, which is set in the local profile alone, and
 * {@code DevConfigurationGuard} refuses to start the application with it under any other profile.
 *
 * <p>This is the one sanctioned place where an OTP code is logged (the exception to NFR-46). It uses its own
 * logger name so log pipelines can drop it, and it logs the mobile number masked.
 */
@Component
@ConditionalOnProperty(name = "gigwork.identity.otp.sender", havingValue = "dev")
class DevLoggingOtpSender implements OtpSender {

	static final String LOGGER_NAME = "gigwork.dev.otp";

	private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

	@Override
	public void send(MobileNumber mobile, String code) {
		log.info("DEV OTP for {} is {}", mobile.masked(), code);
	}

}
