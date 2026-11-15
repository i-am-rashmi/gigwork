package com.gigwork.core.hiring.domain;

/** The caller is a side of the hire but not the one allowed to do this: confirming or declining is for the side that did not start it, withdrawing for the one that did. Answered with 409. */
public class HireWrongSideException extends RuntimeException {

	public HireWrongSideException(String message) {
		super(message);
	}

}
