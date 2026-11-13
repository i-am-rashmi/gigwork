package com.gigwork.core.identity.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.otp.OtpSender;

/** Test double: remembers the last code sent to each number, so tests can complete the login. */
public class CapturingOtpSender implements OtpSender {

	private final Map<String, String> lastCodes = new ConcurrentHashMap<>();
	private final AtomicInteger sent = new AtomicInteger();

	@Override
	public void send(MobileNumber mobile, String code) {
		lastCodes.put(mobile.value(), code);
		sent.incrementAndGet();
	}

	public String lastCode(MobileNumber mobile) {
		String code = lastCodes.get(mobile.value());
		if (code == null) {
			throw new IllegalStateException("no code was sent to " + mobile);
		}
		return code;
	}

	public int sentCount() {
		return sent.get();
	}

}
