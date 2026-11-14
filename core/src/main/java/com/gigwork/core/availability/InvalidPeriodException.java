package com.gigwork.core.availability;

/** A period whose end is not after its start, or that is missing a bound. */
public class InvalidPeriodException extends RuntimeException {

	public InvalidPeriodException() {
		super("a period needs a start and an end after it");
	}

}
