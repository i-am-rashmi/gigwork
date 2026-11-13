package com.gigwork.core.identity.security;

/** The JWT signing key pair is missing, malformed or inconsistent. The application must not start. */
public class JwtKeyConfigurationException extends IllegalStateException {

	public JwtKeyConfigurationException(String message) {
		super(message);
	}

	public JwtKeyConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}

}
