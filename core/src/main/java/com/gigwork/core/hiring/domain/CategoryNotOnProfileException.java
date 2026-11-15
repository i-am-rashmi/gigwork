package com.gigwork.core.hiring.domain;

/** The gig's category is not on the Talent's profile. Answered with 409. */
public class CategoryNotOnProfileException extends RuntimeException {

	public CategoryNotOnProfileException() {
		super("Add this gig's category to your profile to apply.");
	}

}
