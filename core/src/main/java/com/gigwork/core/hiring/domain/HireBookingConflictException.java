package com.gigwork.core.hiring.domain;

/** The Talent has another confirmed hire at that time. Answered with 409. */
public class HireBookingConflictException extends RuntimeException {

	public HireBookingConflictException() {
		super("The Talent is already booked at that time, so this hire can't be confirmed.");
	}

}
