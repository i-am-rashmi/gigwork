package com.gigwork.core.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param mobile an Indian mobile number, e.g. {@code 9876543210} or {@code +91 98765 43210} */
public record OtpRequest(@NotBlank @Size(max = 32) String mobile) {

	/** Never print the number (NFR-46). */
	@Override
	public String toString() {
		return "OtpRequest[mobile=***]";
	}

}
