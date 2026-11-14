package com.gigwork.core.availability.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gigwork.core.availability.persistence.CalendarEntry;

/**
 * One item of the Talent's calendar. Times are UTC instants, end exclusive. {@code startDate} and {@code endDate}
 * (inclusive) are set for all-day blocks; {@code gigId} for bookings.
 *
 * @param type BUSY_BLOCK or BOOKING
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CalendarEntryResponse(String type, UUID id, Instant start, Instant end, boolean allDay, LocalDate startDate,
		LocalDate endDate, UUID gigId) {

	public static CalendarEntryResponse of(CalendarEntry e) {
		return new CalendarEntryResponse(e.type().name(), e.id(), e.start(), e.end(), e.allDay(), e.firstDate(),
				e.lastDate(), e.gigId());
	}

}
