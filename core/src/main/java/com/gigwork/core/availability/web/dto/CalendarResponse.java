package com.gigwork.core.availability.web.dto;

import java.time.LocalDate;
import java.util.List;

import com.gigwork.core.availability.application.CalendarView;

/**
 * The Talent's own calendar for a range of dates (inclusive), sorted by start.
 *
 * @param timeZone the IANA zone the dates are read in (your city's)
 */
public record CalendarResponse(String timeZone, LocalDate from, LocalDate to, List<CalendarEntryResponse> entries) {

	public static CalendarResponse of(CalendarView view) {
		return new CalendarResponse(view.zone().getId(), view.from(), view.to(),
				view.entries().stream().map(CalendarEntryResponse::of).toList());
	}

}
