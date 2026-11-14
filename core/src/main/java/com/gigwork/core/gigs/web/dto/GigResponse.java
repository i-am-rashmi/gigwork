package com.gigwork.core.gigs.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gigwork.core.gigs.application.GigView;
import com.gigwork.core.gigs.domain.GigStatus;
import com.gigwork.core.gigs.domain.WorkMode;

/**
 * A gig as the caller may see it. Never the exact point, a street address or the number of applicants. The Poster's
 * first name is shown to logged-in users only; {@code updatedAt}, {@code closedAt} and {@code closeReason} only to
 * the Poster. Fields that don't apply are left out.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GigResponse(UUID id, String title, String description, UUID categoryId, String categoryName,
		UUID specialisationId, String specialisationName, String languageCode, String languageName, Instant startAt,
		Instant endAt, WorkMode workMode, String locationLabel, UUID cityId, String cityName, Boolean taOffered,
		Long budgetMin, Long budgetMax, GigStatus status, Instant publishedAt, String posterFirstName,
		Instant updatedAt, Instant closedAt, String closeReason) {

	public static GigResponse of(GigView g) {
		return new GigResponse(g.id(), g.title(), g.description(), g.categoryId(), g.categoryName(),
				g.specialisationId(), g.specialisationName(), g.languageCode(), g.languageName(), g.startAt(), g.endAt(),
				g.workMode(), g.locationLabel(), g.cityId(), g.cityName(), g.taOffered(), g.budgetMin(), g.budgetMax(),
				g.status(), g.publishedAt(), g.posterFirstName(), g.updatedAt(), g.closedAt(), g.closeReason());
	}

}
