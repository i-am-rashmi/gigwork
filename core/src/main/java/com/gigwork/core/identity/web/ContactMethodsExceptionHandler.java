package com.gigwork.core.identity.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.gigwork.core.identity.domain.EmailNotAvailableException;

/** Choosing email is a 400 for now. */
@RestControllerAdvice(assignableTypes = ContactMethodsController.class)
class ContactMethodsExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(EmailNotAvailableException.class)
	ProblemDetail email(EmailNotAvailableException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problem.setTitle("Email not available");
		return problem;
	}

}
