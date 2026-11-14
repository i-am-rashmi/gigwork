package com.gigwork.core.availability.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.gigwork.core.availability.domain.BusyBlockLimitException;
import com.gigwork.core.availability.domain.InvalidBusyBlockException;
import com.gigwork.core.availability.domain.ProfileRequiredException;

/** Broken busy block rules are 400, a missing profile is 409; the messages are for the user. */
@RestControllerAdvice(assignableTypes = AvailabilityController.class)
class AvailabilityExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler({ InvalidBusyBlockException.class, BusyBlockLimitException.class })
	ProblemDetail invalid(RuntimeException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid busy block", ex.getMessage());
	}

	@ExceptionHandler(ProfileRequiredException.class)
	ProblemDetail profileRequired(ProfileRequiredException ex) {
		return problem(HttpStatus.CONFLICT, "Profile required", ex.getMessage());
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
