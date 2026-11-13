package com.gigwork.core.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param refreshToken the token from the last login or refresh; it works once. The size limit is generous on
 *                     purpose: anything that is not exactly a token we issued (even a long access token pasted by
 *                     mistake) is answered with 401 by the service; only absurd input is a 400.
 */
public record RefreshRequest(@NotBlank @Size(max = 4096) String refreshToken) {

	/** Never print a token (NFR-46). */
	@Override
	public String toString() {
		return "RefreshRequest[refreshToken=***]";
	}

}
