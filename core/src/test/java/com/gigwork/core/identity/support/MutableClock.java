package com.gigwork.core.identity.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** A clock tests can move, to check expiry and idle timeouts without waiting. */
public class MutableClock extends Clock {

	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());

	public void reset() {
		now.set(Instant.now());
	}

	public void advance(Duration duration) {
		now.updateAndGet(instant -> instant.plus(duration));
	}

	@Override
	public Instant instant() {
		return now.get();
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

}
