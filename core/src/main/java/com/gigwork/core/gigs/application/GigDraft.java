package com.gigwork.core.gigs.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.gigwork.core.gigs.domain.WorkMode;

/**
 * What a Poster submits to publish or replace a gig, before any rule is checked. Optional fields are null when left
 * out; an edit replaces every editable field, so a field left out is cleared.
 *
 * @param localityId on-site gigs: the place, as a locality (give this or {@code lat} and {@code lng})
 * @param lat on-site gigs: the place, as a map point
 * @param taOffered on-site gigs: whether travel allowance is offered; null for remote gigs
 */
public record GigDraft(UUID categoryId, UUID specialisationId, String languageCode, String title, String description,
		Instant startAt, Instant endAt, WorkMode workMode, UUID localityId, Double lat, Double lng, Boolean taOffered,
		BigDecimal budgetMin, BigDecimal budgetMax) {

	/** The same draft for a gig that already exists: its category and work mode are the gig's, not the request's. */
	GigDraft forGig(UUID category, WorkMode mode) {
		return new GigDraft(category, specialisationId, languageCode, title, description, startAt, endAt, mode, localityId,
				lat, lng, taOffered, budgetMin, budgetMax);
	}

}
