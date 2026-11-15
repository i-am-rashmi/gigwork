package com.gigwork.core.hiring.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.hiring.application.HireService;
import com.gigwork.core.hiring.web.dto.CancelHireRequest;
import com.gigwork.core.hiring.web.dto.HireResponse;
import com.gigwork.core.platform.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Hires (FR-27, FR-28): either side starts one, the other confirms it. Needs a token. */
@RestController
@RequestMapping
@Tag(name = "Hires")
class HireController {

	private final HireService hires;

	HireController(HireService hires) {
		this.hires = hires;
	}

	@Operation(summary = "Start a hire for a shortlisted applicant",
			description = "The gig's Poster marks the Talent as hired, or the Talent says they've been hired for this. "
					+ "The other side must confirm within 24 hours or before the gig starts, whichever is earlier; until "
					+ "then the gig stays open but can't be edited. If the other side had already started a hire for this "
					+ "application, this confirms it.")
	@ApiResponse(responseCode = "404", description = "Not an application of yours")
	@ApiResponse(responseCode = "409", description = "Not allowed now (the code says why): not shortlisted, gig not open, "
			+ "another hire pending, or the Talent is booked at that time")
	@PostMapping("/applications/{id}/hire")
	@ResponseStatus(HttpStatus.CREATED)
	HireResponse start(@PathVariable UUID id) {
		return HireResponse.of(hires.start(CurrentAccount.required(), id));
	}

	@Operation(summary = "Confirm a hire the other side started",
			description = "In one step the gig becomes filled and the Talent is booked for the gig's dates. Refused if the "
					+ "hire expired or the Talent is booked at that time; nothing then changes.")
	@ApiResponse(responseCode = "404", description = "Not a hire of yours")
	@ApiResponse(responseCode = "409", description = "Not allowed now (the code says why)")
	@PostMapping("/hires/{id}/confirm")
	HireResponse confirm(@PathVariable UUID id) {
		return HireResponse.of(hires.confirm(CurrentAccount.required(), id));
	}

	@Operation(summary = "Decline a hire the other side started", description = "The gig stays open.")
	@ApiResponse(responseCode = "404", description = "Not a hire of yours")
	@ApiResponse(responseCode = "409", description = "Not allowed now (the code says why)")
	@PostMapping("/hires/{id}/decline")
	HireResponse decline(@PathVariable UUID id) {
		return HireResponse.of(hires.decline(CurrentAccount.required(), id));
	}

	@Operation(summary = "Withdraw a hire you started", description = "The gig stays open.")
	@ApiResponse(responseCode = "404", description = "Not a hire of yours")
	@ApiResponse(responseCode = "409", description = "Not allowed now (the code says why)")
	@PostMapping("/hires/{id}/withdraw")
	HireResponse withdraw(@PathVariable UUID id) {
		return HireResponse.of(hires.withdraw(CurrentAccount.required(), id));
	}

	@Operation(summary = "Cancel a confirmed hire",
			description = "Either side, with a reason. The Talent's booking is released at once; if the gig has not started "
					+ "it reopens so the Poster can hire someone else, otherwise it expires. The other side is notified.")
	@ApiResponse(responseCode = "400", description = "The reason is missing or too long")
	@ApiResponse(responseCode = "404", description = "Not a hire of yours")
	@ApiResponse(responseCode = "409", description = "The hire is not confirmed")
	@PostMapping("/hires/{id}/cancel")
	HireResponse cancel(@PathVariable UUID id, @RequestBody(required = false) CancelHireRequest request) {
		return HireResponse.of(hires.cancel(CurrentAccount.required(), id, request == null ? null : request.reason()));
	}

	@Operation(summary = "A hire", description = "Either side of the hire.")
	@ApiResponse(responseCode = "404", description = "Not a hire of yours")
	@GetMapping("/hires/{id}")
	HireResponse get(@PathVariable UUID id) {
		return HireResponse.of(hires.hire(CurrentAccount.required(), id));
	}

}
