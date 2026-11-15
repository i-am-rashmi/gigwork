package com.gigwork.core.hiring.domain;

/** A rule about the request is broken (note, page size, cursor); the message says which. Answered with 400. */
public class InvalidApplicationException extends RuntimeException {

	public InvalidApplicationException(String message) {
		super(message);
	}

}
