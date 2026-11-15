package com.gigwork.core.hiring.domain;

/** Contact details can't be viewed now. The reason is never said, so nothing is learned from the refusal. Answered with 409. */
public class ContactNotAvailableException extends RuntimeException {

	public ContactNotAvailableException() {
		super("Contact details aren't available.");
	}

}
