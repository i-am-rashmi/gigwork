package com.gigwork.core.profile.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.platform.CurrentAccount;
import com.gigwork.core.profile.application.LocationCommand;
import com.gigwork.core.profile.application.TalentProfileService;
import com.gigwork.core.profile.application.WorkCommand;
import com.gigwork.core.profile.web.dto.LocationRequest;
import com.gigwork.core.profile.web.dto.OwnerProfileResponse;
import com.gigwork.core.profile.web.dto.PortfolioRequest;
import com.gigwork.core.profile.web.dto.VisibilityRequest;
import com.gigwork.core.profile.web.dto.WorkRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * The signed-in Talent's own profile (FR-4 to FR-6). Needs a token. Each PUT replaces one whole section and returns
 * the profile; the profile becomes ACTIVE by itself once work and location are both saved.
 */
@RestController
@RequestMapping("/me/talent-profile")
@Tag(name = "Talent profile", description = "Your own Talent profile, saved in three sections")
class MeTalentProfileController {

	private final TalentProfileService profiles;

	MeTalentProfileController(TalentProfileService profiles) {
		this.profiles = profiles;
	}

	@Operation(summary = "Your profile, with everything you may see (never raw coordinates)")
	@ApiResponse(responseCode = "404", description = "Nothing saved yet")
	@GetMapping
	ResponseEntity<OwnerProfileResponse> get() {
		return profiles.ownerProfile(CurrentAccount.required()).map(OwnerProfileResponse::of)
				.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@Operation(summary = "Save the work section",
			description = "Individual or group, headline, bio, 1 to 5 categories, up to 10 specialisations of them, "
					+ "and languages (only if a category is language-relevant). Replaces the whole section.")
	@ApiResponse(responseCode = "400", description = "A rule is broken; the message says which")
	@PutMapping("/work")
	OwnerProfileResponse saveWork(@Valid @RequestBody WorkRequest request) {
		return OwnerProfileResponse.of(profiles.saveWork(CurrentAccount.required(),
				new WorkCommand(request.profileType(), request.memberCount(), request.headline(), request.bio(),
						request.categoryIds(), request.specialisationIds(), request.languageCodes())));
	}

	@Operation(summary = "Save the location section",
			description = "Exactly one of localityId or point {lat, lng}, plus service radius (1-50 km) and "
					+ "willing-to-travel. A point is stored rounded to 3 decimals.")
	@ApiResponse(responseCode = "400", description = "Both or neither of localityId and point, an unknown locality, or a point too far from any area we serve")
	@PutMapping("/location")
	OwnerProfileResponse saveLocation(@Valid @RequestBody LocationRequest request) {
		var point = request.point();
		return OwnerProfileResponse.of(profiles.saveLocation(CurrentAccount.required(),
				new LocationCommand(request.localityId(), point == null ? null : point.lat(),
						point == null ? null : point.lng(), request.serviceRadiusKm(), request.willingToTravel())));
	}

	@Operation(summary = "Save the portfolio section",
			description = "Up to 10 links, in display order. https:// is added when missing. URL shorteners and "
					+ "contact links are refused. The links are never opened by the server.")
	@ApiResponse(responseCode = "400", description = "A link is not acceptable; the message says why")
	@PutMapping("/portfolio")
	OwnerProfileResponse savePortfolio(@Valid @RequestBody PortfolioRequest request) {
		return OwnerProfileResponse.of(profiles.savePortfolio(CurrentAccount.required(), request.links()));
	}

	@Operation(summary = "Hide or show your profile",
			description = "A hidden profile can't be found by anyone else. Showing it again restores ACTIVE (or "
					+ "INCOMPLETE if a section is still missing).")
	@PutMapping("/visibility")
	OwnerProfileResponse setVisibility(@Valid @RequestBody VisibilityRequest request) {
		return OwnerProfileResponse.of(profiles.setHidden(CurrentAccount.required(), request.hidden()));
	}

}
