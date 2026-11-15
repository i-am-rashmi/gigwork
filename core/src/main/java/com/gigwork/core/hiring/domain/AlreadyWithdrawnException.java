package com.gigwork.core.hiring.domain;

/** The application is already withdrawn. Answered with 409. */
public class AlreadyWithdrawnException extends RuntimeException {

	public AlreadyWithdrawnException() {
		super("This application is already withdrawn.");
	}

}
