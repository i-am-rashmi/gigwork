package com.gigwork.core.availability.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * The rules for a new busy block (FR-7), as pure functions. A block is a half-open range {@code [start, end)}. It
 * must not lie entirely in the past, must start within a year, and be at most 90 days long. An all-day block is
 * given as first and last date (both inclusive) and becomes midnight to midnight in a time zone. Times are cut to
 * microseconds, PostgreSQL's precision.
 */
public final class BusyBlockRules {

	/** @param maxLengthDays longest block; @param maxAheadYears how far ahead a block may start */
	public record Limits(int maxLengthDays, int maxAheadYears) {

		public static final Limits DEFAULT = new Limits(90, 1);
	}

	/** A valid new block; the dates are set only for an all-day block. */
	public record NewBlock(Instant start, Instant end, boolean allDay, LocalDate firstDate, LocalDate lastDate) {
	}

	private BusyBlockRules() {
	}

	public static NewBlock timed(OffsetDateTime start, OffsetDateTime end, Instant now, Limits limits) {
		if (start == null || end == null) {
			throw new InvalidBusyBlockException("A timed block needs a start and an end.");
		}
		Instant from = start.toInstant().truncatedTo(ChronoUnit.MICROS);
		Instant to = end.toInstant().truncatedTo(ChronoUnit.MICROS);
		if (!to.isAfter(from)) {
			throw new InvalidBusyBlockException("The end must be after the start.");
		}
		if (Duration.between(from, to).compareTo(Duration.ofDays(limits.maxLengthDays())) > 0) {
			throw new InvalidBusyBlockException("A busy block can be at most " + limits.maxLengthDays() + " days long.");
		}
		checkWindow(from, to, now, limits);
		return new NewBlock(from, to, false, null, null);
	}

	public static NewBlock allDay(LocalDate firstDate, LocalDate lastDate, ZoneId zone, Instant now, Limits limits) {
		if (firstDate == null || lastDate == null) {
			throw new InvalidBusyBlockException("An all-day block needs a first and a last date.");
		}
		if (lastDate.isBefore(firstDate)) {
			throw new InvalidBusyBlockException("The last date can't be before the first date.");
		}
		long days = ChronoUnit.DAYS.between(firstDate, lastDate) + 1;
		if (days > limits.maxLengthDays()) {
			throw new InvalidBusyBlockException("A busy block can be at most " + limits.maxLengthDays() + " days long.");
		}
		Instant from = firstDate.atStartOfDay(zone).toInstant();
		Instant to = lastDate.plusDays(1).atStartOfDay(zone).toInstant();
		checkWindow(from, to, now, limits);
		return new NewBlock(from, to, true, firstDate, lastDate);
	}

	private static void checkWindow(Instant from, Instant to, Instant now, Limits limits) {
		if (!to.isAfter(now)) {
			throw new InvalidBusyBlockException("A busy block can't be entirely in the past.");
		}
		Instant latestStart = now.atOffset(ZoneOffset.UTC).plusYears(limits.maxAheadYears()).toInstant();
		if (from.isAfter(latestStart)) {
			throw new InvalidBusyBlockException("A busy block must start within " + limits.maxAheadYears() + " year from now.");
		}
	}

}
