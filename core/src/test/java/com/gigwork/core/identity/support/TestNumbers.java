package com.gigwork.core.identity.support;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import com.gigwork.core.identity.domain.MobileNumber;

/** Unique valid mobile numbers, so tests sharing one database never meet each other's accounts. */
public final class TestNumbers {

	private static final AtomicLong NEXT = new AtomicLong(ThreadLocalRandom.current().nextLong(500_000_000L));

	private TestNumbers() {
	}

	public static MobileNumber next() {
		long n = NEXT.incrementAndGet() % 1_000_000_000L;
		return MobileNumber.parse("9" + String.format("%09d", n));
	}

}
