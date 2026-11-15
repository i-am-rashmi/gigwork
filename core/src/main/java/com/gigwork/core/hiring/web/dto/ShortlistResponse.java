package com.gigwork.core.hiring.web.dto;

import java.util.UUID;

import com.gigwork.core.hiring.application.ShortlistState;

/** The Poster's view of a shortlist entry right after a change. No contact details. */
public record ShortlistResponse(UUID applicationId, boolean shortlisted, boolean posterShares, boolean talentConsented) {

	public static ShortlistResponse of(ShortlistState s) {
		return new ShortlistResponse(s.applicationId(), s.shortlisted(), s.posterShares(), s.talentConsented());
	}

}
