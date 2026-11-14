package com.gigwork.core.availability;

/** The Talent already has an active booking that overlaps the requested period (FR-29, NFR-14). */
public class BookingConflictException extends RuntimeException {

	public BookingConflictException(Throwable cause) {
		super("the talent already has an active booking that overlaps this period", cause);
	}

}
