package com.gigwork.core.hiring.domain;

import java.util.List;

import com.gigwork.core.availability.ConflictingPeriod;

/**
 * The gig overlaps something in the Talent's calendar. The first attempt is refused with the periods so the Talent
 * can confirm (FR-23). Shown to that Talent only; answered with 409.
 */
public class BusyPeriodsException extends RuntimeException {

	private final transient List<ConflictingPeriod> periods;

	public BusyPeriodsException(List<ConflictingPeriod> periods) {
		super("You're busy during this gig. Apply again with acknowledgeBusy set to true to apply anyway.");
		this.periods = List.copyOf(periods);
	}

	public List<ConflictingPeriod> periods() {
		return periods;
	}

}
