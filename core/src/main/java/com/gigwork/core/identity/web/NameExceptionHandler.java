package com.gigwork.core.identity.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.gigwork.core.identity.domain.InvalidFirstNameException;

/** Error responses of the name endpoint as problem details; the text never echoes what was sent. */
@RestControllerAdvice(assignableTypes = NameController.class)
class NameExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(InvalidFirstNameException.class)
	ProblemDetail invalidName(InvalidFirstNameException ex) {
		String detail = ex.reason() == InvalidFirstNameException.Reason.CONTACT_DETAILS
				? "Contact details can't be in your profile. Posters reach you through GigWork."
				: "Enter a first name of 1 to 50 characters: letters, spaces, apostrophes, hyphens and dots.";
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
		problem.setTitle("Invalid first name");
		return problem;
	}

}
