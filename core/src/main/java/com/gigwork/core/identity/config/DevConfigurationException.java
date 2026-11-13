package com.gigwork.core.identity.config;

/** Thrown at startup when development-only configuration is present outside the local and test profiles. */
public class DevConfigurationException extends IllegalStateException {

	public DevConfigurationException(String message) {
		super(message);
	}

}
