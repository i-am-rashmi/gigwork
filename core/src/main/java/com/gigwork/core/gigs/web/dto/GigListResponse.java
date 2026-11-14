package com.gigwork.core.gigs.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gigwork.core.gigs.application.GigPage;

/** A page of gigs. {@code nextCursor} is left out on the last page; pass it back as {@code cursor} for the next one. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GigListResponse(List<GigResponse> items, String nextCursor) {

	public static GigListResponse of(GigPage page) {
		return new GigListResponse(page.items().stream().map(GigResponse::of).toList(), page.nextCursor());
	}

}
