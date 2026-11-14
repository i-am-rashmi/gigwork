package com.gigwork.core.reference.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.reference.ReferenceData;
import com.gigwork.core.reference.web.dto.CategoryResponse;
import com.gigwork.core.reference.web.dto.CityResponse;
import com.gigwork.core.reference.web.dto.LanguageResponse;
import com.gigwork.core.reference.web.dto.LocalityResponse;
import com.gigwork.core.reference.web.dto.ZoneResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** The reference lists everyone needs before signing up (FR-4, FR-5, FR-34). Public; the /v1 prefix is added by the platform. */
@RestController
@RequestMapping
@Validated
@Tag(name = "Reference data", description = "Categories, languages, cities, zones and localities (live ones only)")
@SecurityRequirements // public: no bearer token needed
class ReferenceController {

	/** Digits (a PIN prefix, at least one), or text of at least two characters once trimmed. */
	static final String QUERY_PATTERN = "^\\s*(?:[0-9]+|\\S.*\\S)\\s*$";

	private final ReferenceData reference;

	ReferenceController(ReferenceData reference) {
		this.reference = reference;
	}

	@Operation(summary = "Live categories with their live specialisations, in display order")
	@GetMapping("/categories")
	List<CategoryResponse> categories() {
		return reference.liveCategories().stream().map(CategoryResponse::of).toList();
	}

	@Operation(summary = "Live languages (ISO 639 codes), in display order")
	@GetMapping("/languages")
	List<LanguageResponse> languages() {
		return reference.languages().stream().map(LanguageResponse::of).toList();
	}

	@Operation(summary = "Live cities")
	@GetMapping("/cities")
	List<CityResponse> cities() {
		return reference.liveCities().stream().map(CityResponse::of).toList();
	}

	@Operation(summary = "The zones of a live city (names only)")
	@ApiResponse(responseCode = "404", description = "No live city with this id")
	@GetMapping("/cities/{id}/zones")
	ResponseEntity<List<ZoneResponse>> zones(@PathVariable UUID id) {
		if (reference.liveCities().stream().noneMatch(city -> city.id().equals(id))) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(reference.zones(id).stream().map(ZoneResponse::of).toList());
	}

	@Operation(summary = "Find an area by name or PIN as the user types",
			description = "Text of at least 2 characters matches the start of words of the name, ignoring case and "
					+ "accents; digits (at least 1) match the start of a PIN. Names are not unique, so the same name "
					+ "can come back more than once. A locality is a search shortcut only.")
	@ApiResponse(responseCode = "400", description = "q too short, or limit outside 1..50")
	@GetMapping("/localities")
	List<LocalityResponse> localities(
			@RequestParam @Pattern(regexp = QUERY_PATTERN, message = "must be at least 2 characters, or digits") String q,
			@RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
		return reference.searchLocalities(q, limit).stream().map(LocalityResponse::of).toList();
	}

}
