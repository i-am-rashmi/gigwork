package com.gigwork.core.identity.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.identity.application.AuthenticationService;
import com.gigwork.core.identity.application.AuthenticationService.LoginResult;
import com.gigwork.core.identity.application.AuthTokens;
import com.gigwork.core.identity.application.OtpService;
import com.gigwork.core.identity.application.OtpService.IssuedChallenge;
import com.gigwork.core.identity.application.SessionService;
import com.gigwork.core.identity.domain.InvalidOtpException;
import com.gigwork.core.identity.domain.InvalidRefreshTokenException;
import com.gigwork.core.identity.domain.MobileNumber;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Sign up and log in (FR-1, FR-3). The /v1 prefix is added for all controllers by the platform module. */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Sign up and log in with a mobile number and a one-time code")
@SecurityRequirements // these endpoints are public: no bearer token needed
class AuthController {

	private final OtpService otp;
	private final AuthenticationService authentication;
	private final SessionService sessions;

	AuthController(OtpService otp, AuthenticationService authentication, SessionService sessions) {
		this.otp = otp;
		this.authentication = authentication;
		this.sessions = sessions;
	}

	/** Sends a one-time code to the number. The same call serves new and returning users. */
	@Operation(summary = "Send a one-time code",
			description = "Indian mobile numbers only. The code is valid for 5 minutes and allows 5 attempts.")
	@PostMapping("/otp/request")
	@ResponseStatus(HttpStatus.ACCEPTED)
	OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequest request) {
		MobileNumber mobile = MobileNumber.parse(request.mobile());
		IssuedChallenge challenge = otp.request(mobile);
		return new OtpRequestResponse(challenge.challengeId(), OtpService.CODE_LIFETIME.toSeconds());
	}

	/** Checks the code. A new number signs up, a known one logs in; both get tokens. */
	@Operation(summary = "Verify the code and get tokens",
			description = "A new number signs up, a known one logs in. 401 for any wrong, expired or used code.")
	@PostMapping("/otp/verify")
	ResponseEntity<LoginResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
		MobileNumber mobile = MobileNumber.parse(request.mobile());
		// the service has committed (and counted the attempt) before we turn "empty" into a 401
		LoginResult login = authentication.verifyOtp(mobile, request.challengeId(), request.code())
				.orElseThrow(InvalidOtpException::new);
		return noStore(LoginResponse.of(login.tokens(), login.newAccount()));
	}

	/** Exchanges a refresh token for a new access token and a new refresh token. */
	@Operation(summary = "Refresh the tokens",
			description = "The refresh token works once: the response carries its replacement.")
	@PostMapping("/token/refresh")
	ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		AuthTokens tokens = sessions.refresh(request.refreshToken()).orElseThrow(InvalidRefreshTokenException::new);
		return noStore(RefreshResponse.of(tokens));
	}

	/** Responses that carry tokens must never be cached. */
	private static <T> ResponseEntity<T> noStore(T body) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
	}

}
