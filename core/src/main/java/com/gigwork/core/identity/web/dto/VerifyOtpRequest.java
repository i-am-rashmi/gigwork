package com.gigwork.core.identity.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param mobile      the number the code was sent to
 * @param challengeId from the response to the code request
 * @param code        the six digits
 */
public record VerifyOtpRequest(@NotBlank @Size(max = 32) String mobile, @NotNull UUID challengeId,
		@NotBlank @Pattern(regexp = "[0-9]{6}", message = "must be 6 digits") String code) {

	/** Never print the number or the code (NFR-46). */
	@Override
	public String toString() {
		return "VerifyOtpRequest[challengeId=" + challengeId + "]";
	}

}
