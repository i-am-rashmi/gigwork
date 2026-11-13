package com.gigwork.core.identity.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/** A clock tests can move, to check expiry and idle timeouts without waiting. */
public class MutableClock extends Clock {

	private final AtomicReference<Instant> now = new AtomicReference<>(micros(Instant.now()));

	/** Like the production clock, whole microseconds only (PostgreSQL's precision). */
	private static Instant micros(Instant instant) {
		return instant.truncatedTo(ChronoUnit.MICROS);
	}

	public void reset() {
		now.set(micros(Instant.now()));
	}

	/** Sets the time; sub-microsecond digits are dropped, as the production clock would never deliver them. */
	public void set(Instant instant) {
		now.set(micros(instant));
	}

	public void advance(Duration duration) {
		now.updateAndGet(instant -> micros(instant.plus(duration)));
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
