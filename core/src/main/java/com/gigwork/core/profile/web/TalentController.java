package com.gigwork.core.profile.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.platform.CurrentAccount;
import com.gigwork.core.profile.application.TalentProfileService;
import com.gigwork.core.profile.web.dto.TalentProfileResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** A Talent's public profile (FR-8, FR-9). Public: no token needed, but a token that is sent must be valid. */
@RestController
@RequestMapping("/talent")
@Tag(name = "Talent profile")
@SecurityRequirements // public: no bearer token needed (a token, if sent, shows more)
class TalentController {

	private final TalentProfileService profiles;

	TalentController(TalentProfileService profiles) {
		this.profiles = profiles;
	}

	@Operation(summary = "View a Talent's profile",
			description = "Visitors see categories, specialisations, languages, headline, bio, type, member count, "
					+ "location label and city, and only the types of portfolio links. Logged-in users also see the "
					+ "first name and the links. Coordinates, radius, travel preference, status and contact details "
					+ "are never shown.")
	@ApiResponse(responseCode = "404", description = "No such profile, or it is incomplete or hidden (unless you own it)")
	@GetMapping("/{accountId}")
	ResponseEntity<TalentProfileResponse> view(@PathVariable UUID accountId) {
		return profiles.publicProfile(accountId, CurrentAccount.optional()).map(TalentProfileResponse::of)
				.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

}
