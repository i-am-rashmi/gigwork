package com.gigwork.core.hiring.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.hiring.application.ShortlistService;
import com.gigwork.core.hiring.web.dto.ConsentRequest;
import com.gigwork.core.hiring.web.dto.ConsentResponse;
import com.gigwork.core.hiring.web.dto.PosterShareRequest;
import com.gigwork.core.hiring.web.dto.ShortlistRequest;
import com.gigwork.core.hiring.web.dto.ShortlistResponse;
import com.gigwork.core.platform.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Shortlisting and consent (FR-25, FR-25a, FR-26). Needs a token. Never returns contact details. */
@RestController
@RequestMapping("/applications/{id}/shortlist")
@Tag(name = "Shortlist")
class ShortlistController {

	private final ShortlistService shortlists;

	ShortlistController(ShortlistService shortlists) {
		this.shortlists = shortlists;
	}

	@Operation(summary = "Shortlist an applicant",
			description = "Only the gig's Poster, for an active application on an open gig. Also adds a removed Talent "
					+ "again; their consent is then asked again. Sharing your own contact needs a contact method. At most "
					+ "10 shortlisted per gig and 20 shortlist actions (adding or removing) in 24 hours. The Talent is notified.")
	@ApiResponse(responseCode = "404", description = "Not an application on your gig")
	@ApiResponse(responseCode = "409", description = "Not allowed now (the code says why), or the gig has 10 shortlisted")
	@ApiResponse(responseCode = "429", description = "20 shortlist actions in 24 hours")
	@PostMapping
	ShortlistResponse shortlist(@PathVariable UUID id, @Valid @RequestBody ShortlistRequest request) {
		return ShortlistResponse.of(shortlists.shortlist(CurrentAccount.required(), id, request.shareMyContact()));
	}

	@Operation(summary = "Remove a Talent from the shortlist",
			description = "Only the gig's Poster. Contact details stop being visible to both at once. No notification. "
					+ "Always allowed; it counts towards the 20 actions in 24 hours.")
	@ApiResponse(responseCode = "404", description = "Not an application on your gig")
	@ApiResponse(responseCode = "409", description = "Not shortlisted")
	@PostMapping("/remove")
	ResponseEntity<Void> remove(@PathVariable UUID id) {
		shortlists.remove(CurrentAccount.required(), id);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Share or stop sharing your contact with a shortlisted Talent",
			description = "Only the gig's Poster, while the Talent is shortlisted. Can change at any time; stopping ends "
					+ "the Talent's access at once. Sharing needs a contact method.")
	@ApiResponse(responseCode = "404", description = "Not an application on your gig")
	@ApiResponse(responseCode = "409", description = "Not shortlisted, or no contact method")
	@PutMapping("/poster-share")
	ShortlistResponse posterShare(@PathVariable UUID id, @Valid @RequestBody PosterShareRequest request) {
		return ShortlistResponse.of(shortlists.setPosterShares(CurrentAccount.required(), id, request.share()));
	}

	@Operation(summary = "Consent to reveal your contact to the Poster, or withdraw",
			description = "Only the applicant, while shortlisted. Withdrawing ends the Poster's access at once, with no "
					+ "notification. Consenting needs a contact method.")
	@ApiResponse(responseCode = "404", description = "Not your application")
	@ApiResponse(responseCode = "409", description = "Not shortlisted, or no contact method")
	@PutMapping("/consent")
	ConsentResponse consent(@PathVariable UUID id, @Valid @RequestBody ConsentRequest request) {
		return new ConsentResponse(shortlists.setConsent(CurrentAccount.required(), id, request.consent()));
	}

}
