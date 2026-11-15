package com.gigwork.core.identity.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.identity.application.ContactMethodService;
import com.gigwork.core.identity.persistence.ContactMethodStore.Chosen;
import com.gigwork.core.identity.web.dto.ContactMethodsRequest;
import com.gigwork.core.identity.web.dto.ContactMethodsResponse;
import com.gigwork.core.platform.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** The contact methods the signed-in user is willing to share (FR-2). Needs a token; never returns the number. */
@RestController
@RequestMapping("/me/contact-methods")
@Tag(name = "Account", description = "The signed-in user's own account")
class ContactMethodsController {

	private final ContactMethodService methods;

	ContactMethodsController(ContactMethodService methods) {
		this.methods = methods;
	}

	@Operation(summary = "Choose the contact methods I'm willing to share",
			description = "WhatsApp and call use your verified mobile number. Email is not available until email "
					+ "verification exists. Sharing contact details, or agreeing to share them, needs at least one method.")
	@ApiResponse(responseCode = "400", description = "Email was chosen, or a value is missing")
	@PutMapping
	ContactMethodsResponse save(@Valid @RequestBody ContactMethodsRequest request) {
		return of(methods.save(CurrentAccount.required(), request.whatsapp(), request.call(), request.email()));
	}

	@Operation(summary = "Which contact methods I share", description = "None until you choose.")
	@GetMapping
	ContactMethodsResponse get() {
		return of(methods.chosen(CurrentAccount.required()));
	}

	private static ContactMethodsResponse of(Chosen chosen) {
		return new ContactMethodsResponse(chosen.whatsapp(), chosen.call(), chosen.email());
	}

}
