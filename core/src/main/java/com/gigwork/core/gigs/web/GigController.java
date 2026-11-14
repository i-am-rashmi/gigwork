package com.gigwork.core.gigs.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.gigs.application.GigDraft;
import com.gigwork.core.gigs.application.GigService;
import com.gigwork.core.gigs.web.dto.CancelRequest;
import com.gigwork.core.gigs.web.dto.GigRequest;
import com.gigwork.core.gigs.web.dto.GigResponse;
import com.gigwork.core.platform.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Publishing, editing and cancelling gigs (FR-15, FR-16, FR-18, FR-19). These need a token. */
@RestController
@RequestMapping("/gigs")
@Tag(name = "Gigs")
class GigController {

	private final GigService gigs;

	GigController(GigService gigs) {
		this.gigs = gigs;
	}

	static GigDraft draft(GigRequest r) {
		GigRequest.Location place = r.location();
		GigRequest.Point point = place == null ? null : place.point();
		return new GigDraft(r.categoryId(), r.specialisationId(), r.languageCode(), r.title(), r.description(),
				r.startAt().toInstant(), r.endAt().toInstant(), r.workMode(), place == null ? null : place.localityId(),
				point == null ? null : point.lat(), point == null ? null : point.lng(), r.taOffered(), r.budgetMin(),
				r.budgetMax());
	}

	@Operation(summary = "Publish a gig",
			description = "The gig starts OPEN. It must start in the future, at most 1 year ahead, and last at most "
					+ "14 days. The title and description may not contain phone numbers, email addresses or contact "
					+ "links. An on-site gig needs a location (a locality, or a map point stored to 3 decimals) and the "
					+ "travel-allowance flag; a remote gig has neither. You need a first name.")
	@ApiResponse(responseCode = "400", description = "A rule is broken; the message says which")
	@ApiResponse(responseCode = "409", description = "You have no first name yet")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	GigResponse publish(@Valid @RequestBody GigRequest request) {
		return GigResponse.of(gigs.publish(CurrentAccount.required(), draft(request)));
	}

	@Operation(summary = "Edit an open gig",
			description = "Replaces every editable field (a field left out is cleared). The category and the work "
					+ "mode must be the gig's current ones. Applicants are told about a change of date, time or location.")
	@ApiResponse(responseCode = "400", description = "A rule is broken, or the category or work mode differ")
	@ApiResponse(responseCode = "404", description = "No such gig, or it is not yours")
	@ApiResponse(responseCode = "409", description = "The gig is no longer open, or it has started")
	@PutMapping("/{id}")
	GigResponse edit(@PathVariable UUID id, @Valid @RequestBody GigRequest request) {
		return GigResponse.of(gigs.edit(CurrentAccount.required(), id, draft(request)));
	}

	@Operation(summary = "Cancel an open gig", description = "Gigs are never deleted; a cancelled gig is kept.")
	@ApiResponse(responseCode = "404", description = "No such gig, or it is not yours")
	@ApiResponse(responseCode = "409", description = "The gig is no longer open")
	@PostMapping("/{id}/cancel")
	GigResponse cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) CancelRequest request) {
		return GigResponse.of(gigs.cancel(CurrentAccount.required(), id, request == null ? null : request.reason()));
	}

}
