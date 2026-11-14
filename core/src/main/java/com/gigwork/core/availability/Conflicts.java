package com.gigwork.core.availability;

import java.util.List;

/**
 * What the apply warning needs (FR-23): whether anything in the Talent's calendar overlaps, and when. Deliberately
 * no IDs or other details of the blocks or bookings. Busy blocks are private to the Talent, so show this to that
 * Talent only.
 */
public record Conflicts(boolean hasConflict, List<ConflictingPeriod> periods) {

	public static Conflicts of(List<ConflictingPeriod> periods) {
		return new Conflicts(!periods.isEmpty(), List.copyOf(periods));
	}

}
