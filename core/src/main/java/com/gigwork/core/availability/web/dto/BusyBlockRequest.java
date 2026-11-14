package com.gigwork.core.availability.web.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A busy block: either a timed one ({@code start} and {@code end}, ISO-8601 with a UTC offset, end exclusive) or an
 * all-day one ({@code allDay: true} with {@code startDate} and {@code endDate}, both inclusive, so a one-day block
 * has the same date twice; read in the time zone of your city). Give exactly one of the two forms.
 */
public record BusyBlockRequest(OffsetDateTime start, OffsetDateTime end, Boolean allDay, LocalDate startDate,
		LocalDate endDate) {
}
