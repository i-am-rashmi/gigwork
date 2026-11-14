package com.gigwork.core.gigs.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.gigwork.core.platform.ContactDetails;

/**
 * The rules of a gig that need no database (FR-16): what the schema can't hold, because it depends on the current
 * time or on settings, and the text rules. Each failure is an {@link InvalidGigException} with a message for the user.
 */
public final class GigRules {

	public static final int TITLE_MAX = 100;
	public static final int DESCRIPTION_MAX = 2000;

	private GigRules() {
	}

	/** Trimmed, 1 to {@code max} characters, and without phone numbers, email addresses or contact links. */
	public static String text(String raw, String field, int max) {
		String text = raw == null ? "" : raw.strip();
		if (text.isEmpty()) {
			throw new InvalidGigException("The " + field + " is required.");
		}
		if (text.codePointCount(0, text.length()) > max) {
			throw new InvalidGigException("The " + field + " can be at most " + max + " characters.");
		}
		if (ContactDetails.appearsIn(text)) {
			throw new InvalidGigException("The " + field + " may not contain phone numbers, email addresses or "
					+ "contact links. Talent reach you through GigWork, and contact details are shared only when you choose.");
		}
		return text;
	}

	/**
	 * Starts in the future, at most {@code maxMonthsAhead} months ahead, ends after it starts and lasts at most
	 * {@code maxLengthDays} days.
	 */
	public static void dates(Instant start, Instant end, Instant now, int maxMonthsAhead, int maxLengthDays) {
		if (start == null || end == null) {
			throw new InvalidGigException("A start and an end date and time are required.");
		}
		if (!start.isAfter(now)) {
			throw new InvalidGigException("The gig must start in the future.");
		}
		if (start.isAfter(now.atZone(ZoneOffset.UTC).plusMonths(maxMonthsAhead).toInstant())) {
			throw new InvalidGigException("The gig can start at most " + maxMonthsAhead + " months from now.");
		}
		if (!end.isAfter(start)) {
			throw new InvalidGigException("The gig must end after it starts.");
		}
		if (Duration.between(start, end).compareTo(Duration.ofDays(maxLengthDays)) > 0) {
			throw new InvalidGigException("A gig can last at most " + maxLengthDays + " days.");
		}
	}

	/** Whole rupees, not negative, minimum not above maximum; both given or both left out. */
	public static Budget budget(BigDecimal min, BigDecimal max) {
		if (min == null && max == null) {
			return null;
		}
		if (min == null || max == null) {
			throw new InvalidGigException("Give both a minimum and a maximum budget, or neither. "
					+ "For a fixed amount, make them equal.");
		}
		long low = rupees(min, "minimum");
		long high = rupees(max, "maximum");
		if (low > high) {
			throw new InvalidGigException("The minimum budget can't be above the maximum.");
		}
		return new Budget(low, high);
	}

	private static long rupees(BigDecimal amount, String which) {
		if (amount.signum() < 0) {
			throw new InvalidGigException("The " + which + " budget can't be negative.");
		}
		try {
			return amount.longValueExact();
		}
		catch (ArithmeticException e) {
			throw new InvalidGigException("The " + which + " budget must be a whole number of rupees.");
		}
	}

	/** A budget range in whole rupees. */
	public record Budget(long min, long max) {
	}

}
