package com.gigwork.core.reference.seed;

/** A seed file is missing, malformed or inconsistent. Migrations fail loudly rather than load part of the data. */
public class SeedDataException extends RuntimeException {

	public SeedDataException(String message) {
		super(message);
	}

	public SeedDataException(String message, Throwable cause) {
		super(message, cause);
	}

}
