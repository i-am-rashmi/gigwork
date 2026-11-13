package com.gigwork.core.platform;

/** Path conventions shared by every module's HTTP API. */
public final class ApiPaths {

	/**
	 * Version prefix of every API endpoint (NFR-71: breaking changes get a new version). It is applied once, to
	 * all {@code @RestController}s of the core, by the platform module; controllers declare paths without it.
	 * Actuator endpoints stay unversioned.
	 */
	public static final String V1 = "/v1";

	private ApiPaths() {
	}

}
