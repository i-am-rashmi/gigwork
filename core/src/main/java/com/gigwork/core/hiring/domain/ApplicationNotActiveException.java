package com.gigwork.core.hiring.domain;

/** The application was withdrawn, so it cannot be shortlisted. Answered with 409. */
public class ApplicationNotActiveException extends RuntimeException {

	public ApplicationNotActiveException() {
		super("This application has been withdrawn.");
	}

}
