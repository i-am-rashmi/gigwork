package com.gigwork.core.hiring.web;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.hiring.application.ContactService;
import com.gigwork.core.hiring.web.dto.ContactResponse;
import com.gigwork.core.platform.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Viewing the other side's contact details, after consent (FR-26). Needs a token. Every view is logged. */
@RestController
@RequestMapping("/applications/{id}")
@Tag(name = "Shortlist")
class ContactController {

	private final ContactService contacts;

	ContactController(ContactService contacts) {
		this.contacts = contacts;
	}

	@Operation(summary = "View a shortlisted Talent's contact details",
			description = "Only the gig's Poster, only while the Talent is shortlisted and has consented, the application "
					+ "is active and the gig is open. The view is logged. Never cached.")
	@ApiResponse(responseCode = "404", description = "Not an application on your gig")
	@ApiResponse(responseCode = "409", description = "Not available now (the reason is not said)")
	@GetMapping("/talent-contact")
	ResponseEntity<ContactResponse> talentContact(@PathVariable UUID id) {
		return noStore(ContactResponse.of(contacts.talentContact(CurrentAccount.required(), id)));
	}

	@Operation(summary = "View the Poster's contact details",
			description = "Only the applicant, only while shortlisted and the Poster is sharing, the application is active "
					+ "and the gig is open. The view is logged. Never cached.")
	@ApiResponse(responseCode = "404", description = "Not your application")
	@ApiResponse(responseCode = "409", description = "Not available now (the reason is not said)")
	@GetMapping("/poster-contact")
	ResponseEntity<ContactResponse> posterContact(@PathVariable UUID id) {
		return noStore(ContactResponse.of(contacts.posterContact(CurrentAccount.required(), id)));
	}

	private static ResponseEntity<ContactResponse> noStore(ContactResponse body) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(body);
	}

}
