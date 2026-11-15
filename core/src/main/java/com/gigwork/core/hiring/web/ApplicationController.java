package com.gigwork.core.hiring.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.hiring.application.ApplyService;
import com.gigwork.core.hiring.web.dto.ApplyRequest;
import com.gigwork.core.hiring.web.dto.ApplyResponse;
import com.gigwork.core.platform.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Applying to gigs (FR-23). Needs a token. */
@RestController
@RequestMapping
@Tag(name = "Applications")
class ApplicationController {

	private final ApplyService applying;

	ApplicationController(ApplyService applying) {
		this.applying = applying;
	}

	@Operation(summary = "Apply to a gig",
			description = "Needs an active Talent profile with the gig's category, an open gig that has not started, "
					+ "and not your own gig. If the gig overlaps your busy blocks or bookings the first attempt is "
					+ "refused (409) with the overlapping periods; repeat it with acknowledgeBusy true to apply "
					+ "anyway. After withdrawing you can apply again. At most 30 applications in any 24 hours.")
	@ApiResponse(responseCode = "400", description = "The note is too long or carries contact details")
	@ApiResponse(responseCode = "404", description = "No such gig")
	@ApiResponse(responseCode = "409", description = "Not allowed to apply (the title says why), or you're busy then")
	@ApiResponse(responseCode = "429", description = "The 24-hour limit is reached")
	@PostMapping("/gigs/{gigId}/applications")
	@ResponseStatus(HttpStatus.CREATED)
	ApplyResponse apply(@PathVariable UUID gigId, @RequestBody(required = false) ApplyRequest request) {
		ApplyRequest r = request == null ? new ApplyRequest(null, null) : request;
		return ApplyResponse.of(applying.apply(CurrentAccount.required(), gigId, r.note(),
				Boolean.TRUE.equals(r.acknowledgeBusy())));
	}

}
