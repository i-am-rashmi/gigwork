package com.gigwork.core.identity.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.gigwork.core.identity.domain.InvalidMobileNumberException;
import com.gigwork.core.identity.domain.InvalidOtpException;
import com.gigwork.core.identity.domain.InvalidRefreshTokenException;
import com.gigwork.core.identity.domain.OtpDeliveryUnavailableException;
import com.gigwork.core.identity.domain.OtpRateLimitedException;

/**
 * Error responses of the auth endpoints as RFC 9457 problem details. Messages are fixed text: they never echo
 * the number, the code or a token (NFR-46). Bean Validation failures become 400 through the base class.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
class AuthExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(InvalidMobileNumberException.class)
	ProblemDetail invalidMobileNumber() {
		return problem(HttpStatus.BAD_REQUEST, "Invalid mobile number", "Enter a valid Indian mobile number.");
	}

	@ExceptionHandler(InvalidOtpException.class)
	ProblemDetail invalidOtp() {
		return problem(HttpStatus.UNAUTHORIZED, "Invalid code", "The code is invalid or has expired.");
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	ProblemDetail invalidRefreshToken() {
		return problem(HttpStatus.UNAUTHORIZED, "Invalid refresh token", "The refresh token is invalid or has expired.");
	}

	@ExceptionHandler(OtpDeliveryUnavailableException.class)
	ProblemDetail deliveryUnavailable() {
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Code could not be sent",
				"We could not send a code right now. Please try again later.");
	}

	@ExceptionHandler(OtpRateLimitedException.class)
	ResponseEntity<ProblemDetail> tooManyCodeRequests(OtpRateLimitedException ex) {
		ProblemDetail problem = problem(HttpStatus.TOO_MANY_REQUESTS, "Too many code requests",
				"Too many code requests. Please try again later.");
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfter().toSeconds()))
				.body(problem);
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
