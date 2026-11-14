package com.gigwork.core.reference.application;

/** The snapshot is empty: the seed data did not load. The application must not start and serve empty lists. */
public class ReferenceDataUnavailableException extends RuntimeException {

	public ReferenceDataUnavailableException(String problem) {
		super("reference data is not usable: " + problem
				+ " (were the seed migrations applied and infra/seed packaged into the build?)");
	}

}
