package com.gigwork.core.availability;

import java.time.Instant;

/** A period of the Talent's calendar that overlaps a gig's dates: when, and whether it is their own block or a hire. */
public record ConflictingPeriod(Instant start, Instant end, Kind kind) {

	public enum Kind {
		BUSY_BLOCK, BOOKING
	}

}
