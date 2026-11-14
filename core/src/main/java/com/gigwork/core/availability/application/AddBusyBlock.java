package com.gigwork.core.availability.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A request to add a busy block: either {@code start} and {@code end}, or {@code allDay} with {@code startDate} and
 * {@code endDate} (inclusive). Exactly one of the two forms must be given.
 */
public record AddBusyBlock(OffsetDateTime start, OffsetDateTime end, Boolean allDay, LocalDate startDate,
		LocalDate endDate) {
}
