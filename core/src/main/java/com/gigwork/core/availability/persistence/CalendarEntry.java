package com.gigwork.core.availability.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A busy block or an active booking as the Talent's calendar shows it. {@code gigId} is set for bookings only. */
public record CalendarEntry(Type type, UUID id, Instant start, Instant end, boolean allDay, LocalDate firstDate,
		LocalDate lastDate, UUID gigId) {

	public enum Type {
		BUSY_BLOCK, BOOKING
	}

}
