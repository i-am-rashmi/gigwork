package com.gigwork.core.availability;

import java.time.Instant;
import java.util.UUID;

/**
 * What other modules (Hiring) may ask Availability. Periods are half-open, {@code [start, end)}, so a gig that
 * ends at 14:00 and one that starts at 14:00 do not overlap. Times are kept to microseconds, PostgreSQL's precision.
 */
public interface Availability {

	/**
	 * Which busy blocks and bookings of the Talent overlap the period, for the apply warning (FR-23). Only the
	 * periods are returned, not IDs; show them to that Talent only.
	 *
	 * @throws InvalidPeriodException if {@code end} is not after {@code start}
	 */
	Conflicts conflicts(UUID accountId, Instant start, Instant end);

	/**
	 * Books the period for the Talent and the gig with one INSERT; the database's exclusion constraint decides, so
	 * simultaneous calls cannot both succeed (FR-29, NFR-14). Safe inside the caller's transaction: a refusal rolls
	 * back only this statement, not the caller's work. Idempotent: if this Talent already has an active booking for
	 * this gig with exactly this period, its ID is returned.
	 *
	 * @return the booking ID
	 * @throws BookingConflictException if the Talent has an overlapping active booking
	 * @throws GigAlreadyBookedException if the gig has an active booking for another Talent or period
	 * @throws InvalidPeriodException if {@code end} is not after {@code start}
	 */
	UUID book(UUID accountId, UUID gigId, Instant start, Instant end);

	/**
	 * Cancels the gig's active booking, freeing the Talent's time. Idempotent.
	 *
	 * @return true if this call cancelled a booking, false if there was none active
	 */
	boolean release(UUID gigId);

}
