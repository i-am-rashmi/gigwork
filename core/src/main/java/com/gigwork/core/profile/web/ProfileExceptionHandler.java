package com.gigwork.core.profile.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.gigwork.core.profile.domain.InvalidProfileException;

/** A broken profile rule is a 400 with a message for the user. Bean Validation failures are 400 through the base class. */
@RestControllerAdvice(assignableTypes = { MeTalentProfileController.class, TalentController.class })
class ProfileExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(InvalidProfileException.class)
	ProblemDetail invalidProfile(InvalidProfileException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problem.setTitle("Invalid profile");
		return problem;
	}

}
