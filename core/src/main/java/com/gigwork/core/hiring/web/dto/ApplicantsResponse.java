package com.gigwork.core.hiring.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gigwork.core.hiring.application.Applicant;
import com.gigwork.core.hiring.application.ApplicantsPage;
import com.gigwork.core.profile.ApplicantProfile;

/**
 * A page of a gig's active applications for its Poster (FR-24, NFR-34): note, when applied and the applicant's
 * profile as a logged-in user sees it, and where the shortlist stands (shortlisted, whether you share, whether the
 * Talent consented). Never contact details, the busy warning or anything else about the Talent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicantsResponse(List<Item> items, String nextCursor) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Item(UUID id, UUID talentId, String note, Instant appliedAt, ApplicantProfile profile, boolean shortlisted,
			boolean posterShares, boolean talentConsented) {
	}

	public static ApplicantsResponse of(ApplicantsPage page) {
		return new ApplicantsResponse(page.items().stream().map(ApplicantsResponse::item).toList(), page.nextCursor());
	}

	private static Item item(Applicant a) {
		return new Item(a.id(), a.talentId(), a.note(), a.appliedAt(), a.profile(), a.shortlisted(), a.posterShares(),
				a.talentConsented());
	}

}
