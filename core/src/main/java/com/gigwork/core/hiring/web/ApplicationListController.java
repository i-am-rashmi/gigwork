package com.gigwork.core.hiring.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.hiring.application.ApplicationQueries;
import com.gigwork.core.hiring.web.dto.ApplicantsResponse;
import com.gigwork.core.hiring.web.dto.MyApplicationResponse;
import com.gigwork.core.hiring.web.dto.ShortlistedResponse;
import com.gigwork.core.platform.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The two lists of applications: the Talent's own, and a gig's for its Poster. Both need a token. */
@RestController
@RequestMapping
@Tag(name = "Applications")
class ApplicationListController {

	private final ApplicationQueries queries;

	ApplicationListController(ApplicationQueries queries) {
		this.queries = queries;
	}

	@Operation(summary = "List your applications, newest first",
			description = "Active and withdrawn, each with its gig as it is now: closed gigs (filled, cancelled, "
					+ "expired) stay in the list with their status. Newest first by when you last applied.")
	@ApiResponse(responseCode = "400", description = "A bad cursor or page size")
	@GetMapping("/me/applications")
	MyApplicationResponse mine(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer size) {
		return MyApplicationResponse.of(queries.mine(CurrentAccount.required(), cursor, size));
	}

	@Operation(summary = "List the applicants of your gig, oldest first",
			description = "Only the gig's Poster. Active applications only: note, when they applied and the "
					+ "applicant's profile as a logged-in user sees it. A Talent who paused (hid) their profile "
					+ "still shows while their application is active. Never contact details.")
	@ApiResponse(responseCode = "400", description = "A bad cursor or page size")
	@ApiResponse(responseCode = "404", description = "No such gig of yours")
	@GetMapping("/gigs/{gigId}/applications")
	ApplicantsResponse applicants(@PathVariable UUID gigId, @RequestParam(required = false) String cursor,
			@RequestParam(required = false) Integer size) {
		return ApplicantsResponse.of(queries.applicants(CurrentAccount.required(), gigId, cursor, size));
	}

	@Operation(summary = "List the gigs you are shortlisted for, newest first",
			description = "Your Shortlisted tab: each gig as it is now (closed gigs stay, with their status), the Poster's "
					+ "first name, whether the Poster shares their contact and whether you have consented. Never contact "
					+ "details: those are only returned by the contact view.")
	@ApiResponse(responseCode = "400", description = "A bad cursor or page size")
	@GetMapping("/me/shortlists")
	ShortlistedResponse shortlisted(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer size) {
		return ShortlistedResponse.of(queries.shortlisted(CurrentAccount.required(), cursor, size));
	}

}
