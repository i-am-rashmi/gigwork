package com.gigwork.core.platform;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpMethod;

/**
 * The endpoints of a module that need no access token (ADR-0009). A module declares them as a bean; the platform
 * module collects all of them into the security configuration, so a module never edits that configuration.
 *
 * <p>"Public" means "no token required", not "anonymous only": a request that does carry a token is still checked
 * (invalid or expired gives 401) and identifies the caller, and the module applies its visibility rules.
 * Paths are relative to the {@code /v1} prefix ({@link ApiPaths#V1}), like controller paths, and may use Ant
 * patterns.
 */
public interface PublicEndpoints {

	/** @param method the HTTP method, or {@code null} for every method */
	record Rule(HttpMethod method, String pattern) {

		@Override
		public String toString() {
			return (method == null ? "ANY" : method.name()) + " " + pattern;
		}

	}

	List<Rule> rules();

	static PublicEndpoints get(String... paths) {
		return versioned(HttpMethod.GET, paths);
	}

	static PublicEndpoints post(String... paths) {
		return versioned(HttpMethod.POST, paths);
	}

	/**
	 * Infrastructure paths outside the versioned API (health probes, API docs), for every method. For the platform
	 * module only: modules declare their endpoints with {@link #get} and {@link #post}, under {@code /v1}.
	 */
	static PublicEndpoints infrastructure(String... paths) {
		return of(Arrays.stream(paths).map(path -> new Rule(null, path)).toList());
	}

	private static PublicEndpoints versioned(HttpMethod method, String... paths) {
		return of(Arrays.stream(paths).map(path -> new Rule(method, ApiPaths.V1 + path)).toList());
	}

	private static PublicEndpoints of(List<Rule> rules) {
		List<Rule> copy = List.copyOf(rules);
		return () -> copy;
	}

}
