package com.gigwork.core.gigs.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.gigwork.core.gigs.domain.GigNotFoundException;
import com.gigwork.core.gigs.domain.GigNotOpenException;
import com.gigwork.core.gigs.domain.InvalidGigException;
import com.gigwork.core.gigs.domain.PosterNameRequiredException;

/** Broken rules are 400, a gig that isn't there or isn't the caller's is 404, one that isn't open any more is 409. */
@RestControllerAdvice(assignableTypes = { GigController.class, MyGigsController.class })
class GigsExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(InvalidGigException.class)
	ProblemDetail invalid(InvalidGigException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid gig", ex.getMessage());
	}

	@ExceptionHandler(GigNotFoundException.class)
	ProblemDetail notFound(GigNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Gig not found", ex.getMessage());
	}

	@ExceptionHandler(GigNotOpenException.class)
	ProblemDetail notOpen(GigNotOpenException ex) {
		return problem(HttpStatus.CONFLICT, "Gig not open", ex.getMessage());
	}

	@ExceptionHandler(PosterNameRequiredException.class)
	ProblemDetail nameRequired(PosterNameRequiredException ex) {
		return problem(HttpStatus.CONFLICT, "First name required", ex.getMessage());
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
