package com.gigwork.core.identity.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.identity.application.OtpService;
import com.gigwork.core.identity.application.OtpService.IssuedChallenge;
import com.gigwork.core.identity.domain.MobileNumber;

import jakarta.validation.Valid;

/** Sign up and log in (FR-1, FR-3). The /v1 prefix is added for all controllers by the platform module. */
@RestController
@RequestMapping("/auth")
class AuthController {

	private final OtpService otp;

	AuthController(OtpService otp) {
		this.otp = otp;
	}

	/** Sends a one-time code to the number. The same call serves new and returning users. */
	@PostMapping("/otp/request")
	@ResponseStatus(HttpStatus.ACCEPTED)
	OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequest request) {
		MobileNumber mobile = MobileNumber.parse(request.mobile());
		IssuedChallenge challenge = otp.request(mobile);
		return new OtpRequestResponse(challenge.challengeId(), OtpService.CODE_LIFETIME.toSeconds());
	}

}
