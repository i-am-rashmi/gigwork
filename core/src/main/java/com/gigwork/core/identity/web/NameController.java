package com.gigwork.core.identity.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.identity.application.NameService;
import com.gigwork.core.identity.web.dto.NameRequest;
import com.gigwork.core.identity.web.dto.NameResponse;
import com.gigwork.core.platform.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** The user's own first name, shown to other logged-in users (FR-1, FR-6a). Needs a token. */
@RestController
@RequestMapping("/me/name")
@Tag(name = "Account", description = "The signed-in user's own account")
class NameController {

	private final NameService names;

	NameController(NameService names) {
		this.names = names;
	}

	@Operation(summary = "Set my first name",
			description = "1-50 characters: letters, spaces, apostrophes, hyphens and dots. Contact details are not allowed.")
	@ApiResponse(responseCode = "400", description = "Not a valid first name")
	@PutMapping
	ResponseEntity<NameResponse> setName(@Valid @RequestBody NameRequest request) {
		return names.setFirstName(CurrentAccount.required(), request.firstName())
				.map(name -> ResponseEntity.ok(new NameResponse(name)))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
