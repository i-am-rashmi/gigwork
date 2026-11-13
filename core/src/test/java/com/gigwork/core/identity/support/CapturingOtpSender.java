package com.gigwork.core.identity.support;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.domain.OtpDeliveryUnavailableException;
import com.gigwork.core.identity.otp.OtpSender;

/**
 * Test double: remembers the last code sent to each number, so tests can complete the login. It can also be
 * told to fail or to be slow, and to run a check at the moment of sending. A failed or slow send still records
 * the code, so a test can prove that a code whose delivery failed cannot be used.
 */
public class CapturingOtpSender implements OtpSender {

	private final Map<String, String> lastCodes = new ConcurrentHashMap<>();
	private final AtomicInteger sent = new AtomicInteger();
	private volatile boolean failing;
	private volatile Duration delay = Duration.ZERO;
	private volatile Consumer<MobileNumber> duringSend = mobile -> {
	};

	@Override
	public void send(MobileNumber mobile, String code) {
		lastCodes.put(mobile.value(), code);
		sent.incrementAndGet();
		duringSend.accept(mobile);
		if (!delay.isZero()) {
			try {
				Thread.sleep(delay);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
		if (failing) {
			throw new OtpDeliveryUnavailableException();
		}
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

	/** Makes every following send fail (after recording the code). */
	public void failSends(boolean failing) {
		this.failing = failing;
	}

	/** Makes every following send take this long. */
	public void delaySends(Duration delay) {
		this.delay = delay;
	}

	/** Runs the check at the moment of each send; throw from it to fail the send. */
	public void onSend(Consumer<MobileNumber> check) {
		this.duringSend = check;
	}

	/** Back to normal: no failure, no delay, no check. */
	public void reset() {
		failing = false;
		delay = Duration.ZERO;
		duringSend = mobile -> {
		};
	}

}
