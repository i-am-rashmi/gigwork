package com.gigwork.core.hiring.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.gigwork.core.hiring.domain.AlreadyAppliedException;
import com.gigwork.core.hiring.domain.AlreadyWithdrawnException;
import com.gigwork.core.hiring.domain.ApplicationNotFoundException;
import com.gigwork.core.hiring.domain.ApplyLimitReachedException;
import com.gigwork.core.hiring.domain.BusyPeriodsException;
import com.gigwork.core.hiring.domain.CategoryNotOnProfileException;
import com.gigwork.core.hiring.domain.GigNotOpenForApplicationsException;
import com.gigwork.core.hiring.domain.InvalidApplicationException;
import com.gigwork.core.hiring.domain.OwnGigException;
import com.gigwork.core.hiring.domain.ProfileNotActiveException;

/**
 * Broken rules are 400, something that isn't there or isn't the caller's is 404, a refusal that depends on the
 * state of the profile, the gig or the application is 409 (with a {@code code} clients can act on), the daily limit
 * is 429. The messages are for the user.
 */
@RestControllerAdvice(assignableTypes = { ApplicationController.class, ApplicationListController.class })
class HiringExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(InvalidApplicationException.class)
	ProblemDetail invalid(InvalidApplicationException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid application", ex.getMessage(), "INVALID_APPLICATION");
	}

	@ExceptionHandler(ApplicationNotFoundException.class)
	ProblemDetail notFound(ApplicationNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), "NOT_FOUND");
	}

	@ExceptionHandler(ProfileNotActiveException.class)
	ProblemDetail profileNotActive(ProfileNotActiveException ex) {
		return problem(HttpStatus.CONFLICT, "Profile not active", ex.getMessage(), "PROFILE_NOT_ACTIVE");
	}

	@ExceptionHandler(GigNotOpenForApplicationsException.class)
	ProblemDetail gigNotOpen(GigNotOpenForApplicationsException ex) {
		return problem(HttpStatus.CONFLICT, "Gig not open", ex.getMessage(), "GIG_NOT_OPEN");
	}

	@ExceptionHandler(OwnGigException.class)
	ProblemDetail ownGig(OwnGigException ex) {
		return problem(HttpStatus.CONFLICT, "Your own gig", ex.getMessage(), "OWN_GIG");
	}

	@ExceptionHandler(CategoryNotOnProfileException.class)
	ProblemDetail category(CategoryNotOnProfileException ex) {
		return problem(HttpStatus.CONFLICT, "Category not on your profile", ex.getMessage(), "CATEGORY_NOT_ON_PROFILE");
	}

	@ExceptionHandler(AlreadyAppliedException.class)
	ProblemDetail alreadyApplied(AlreadyAppliedException ex) {
		return problem(HttpStatus.CONFLICT, "Already applied", ex.getMessage(), "ALREADY_APPLIED");
	}

	@ExceptionHandler(AlreadyWithdrawnException.class)
	ProblemDetail alreadyWithdrawn(AlreadyWithdrawnException ex) {
		return problem(HttpStatus.CONFLICT, "Already withdrawn", ex.getMessage(), "ALREADY_WITHDRAWN");
	}

	@ExceptionHandler(BusyPeriodsException.class)
	ProblemDetail busy(BusyPeriodsException ex) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "You're busy then", ex.getMessage(), "BUSY");
		List<Period> periods = ex.periods().stream().map(p -> new Period(p.start(), p.end(), p.kind().name())).toList();
		problem.setProperty("periods", periods);
		return problem;
	}

	@ExceptionHandler(ApplyLimitReachedException.class)
	ProblemDetail limit(ApplyLimitReachedException ex) {
		return problem(HttpStatus.TOO_MANY_REQUESTS, "Too many applications", ex.getMessage(), "APPLY_LIMIT_REACHED");
	}

	/** A period of the Talent's own calendar; only ever sent to that Talent. */
	record Period(java.time.Instant start, java.time.Instant end, String kind) {
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setProperty("code", code);
		return problem;
	}

}
