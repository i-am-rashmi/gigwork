package com.gigwork.core.hiring.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gigwork.core.gigs.GigSummary;
import com.gigwork.core.hiring.application.MyApplication;
import com.gigwork.core.hiring.application.MyApplicationsPage;

/** A page of the caller's applications. {@code nextCursor} is left out on the last page. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyApplicationResponse(List<Item> items, String nextCursor) {

	/** {@code status} is the application's (ACTIVE, WITHDRAWN); the gig's own status is inside {@code gig}. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Item(UUID id, String status, String note, Instant appliedAt, Instant withdrawnAt, Gig gig) {
	}

	/** The gig as it is now. {@code status} is OPEN, FILLED, CANCELLED or EXPIRED. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Gig(UUID id, String title, String categoryName, Instant startAt, Instant endAt, String workMode,
			String locationLabel, String status) {
	}

	public static MyApplicationResponse of(MyApplicationsPage page) {
		return new MyApplicationResponse(page.items().stream().map(MyApplicationResponse::item).toList(), page.nextCursor());
	}

	private static Item item(MyApplication a) {
		GigSummary g = a.gig();
		Gig gig = g == null ? null : new Gig(g.id(), g.title(), g.categoryName(), g.startAt(), g.endAt(), g.workMode(),
				g.locationLabel(), g.status().name());
		return new Item(a.id(), a.status(), a.note(), a.appliedAt(), a.withdrawnAt(), gig);
	}

}
