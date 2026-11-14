package com.gigwork.core.availability.application;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.gigwork.core.availability.persistence.CalendarEntry;

/** A Talent's busy blocks and active bookings overlapping a range of dates, read in {@code zone}. */
public record CalendarView(ZoneId zone, LocalDate from, LocalDate to, List<CalendarEntry> entries) {
}
