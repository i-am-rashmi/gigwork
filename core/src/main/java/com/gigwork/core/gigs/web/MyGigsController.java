package com.gigwork.core.gigs.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.gigs.application.GigService;
import com.gigwork.core.gigs.web.dto.GigListResponse;
import com.gigwork.core.platform.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The signed-in Poster's own gigs. Needs a token. */
@RestController
@RequestMapping("/me/gigs")
@Tag(name = "Gigs")
class MyGigsController {

	private final GigService gigs;

	MyGigsController(GigService gigs) {
		this.gigs = gigs;
	}

	@Operation(summary = "List your gigs, newest first",
			description = "Every status (open, filled, cancelled, expired), with when and why a gig was closed.")
	@ApiResponse(responseCode = "400", description = "A bad cursor or page size")
	@GetMapping
	GigListResponse mine(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer size) {
		return GigListResponse.of(gigs.mine(CurrentAccount.required(), cursor, size));
	}

}
