package com.gigwork.core.hiring.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gigwork.core.gigs.GigSummary;
import com.gigwork.core.hiring.application.ShortlistedItem;
import com.gigwork.core.hiring.application.ShortlistedPage;

/** A page of the Talent's Shortlisted tab. No contact details: they are only ever returned by the contact view. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShortlistedResponse(List<Item> items, String nextCursor) {

	/** {@code posterShares}: whether the Poster shares their contact; {@code consented}: whether you consented. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Item(UUID applicationId, Instant shortlistedAt, String posterFirstName, boolean posterShares,
			boolean consented, Instant consentedAt, Gig gig) {
	}

	/** The gig as it is now. {@code status} is OPEN, FILLED, CANCELLED or EXPIRED. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Gig(UUID id, String title, String categoryName, Instant startAt, Instant endAt, String workMode,
			String locationLabel, String status) {
	}

	public static ShortlistedResponse of(ShortlistedPage page) {
		return new ShortlistedResponse(page.items().stream().map(ShortlistedResponse::item).toList(), page.nextCursor());
	}

	private static Item item(ShortlistedItem i) {
		GigSummary g = i.gig();
		Gig gig = g == null ? null : new Gig(g.id(), g.title(), g.categoryName(), g.startAt(), g.endAt(), g.workMode(),
				g.locationLabel(), g.status().name());
		return new Item(i.applicationId(), i.shortlistedAt(), i.posterFirstName(), i.posterShares(), i.consented(),
				i.consentedAt(), gig);
	}

}
