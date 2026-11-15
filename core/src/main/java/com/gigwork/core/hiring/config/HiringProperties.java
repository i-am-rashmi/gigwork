package com.gigwork.core.hiring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Hiring settings ({@code gigwork.hiring.*}).
 *
 * @param applySubmissionsPerDay NFR-39: the abuse limit, submissions per Talent in any rolling 24 hours
 * @param noteMaxLength FR-23: longest application note, in characters
 * @param defaultPageSize applications per page when the request does not say
 * @param maxPageSize most applications per page
 * @param shortlistPerGig NFR-38: Talent shortlisted at the same time for one gig
 * @param shortlistActionsPerDay NFR-38: a Poster's shortlist actions (adding and removing) in any rolling 24 hours
 * @param lapseBatchSize ADR-0010: pending hires lapsed per transaction by the lapse job
 * @param hiredContactDaysAfterGig FR-26: the hired pair can still view each other's contacts this many days after the gig ends
 */
@ConfigurationProperties("gigwork.hiring")
public record HiringProperties(@DefaultValue("30") int applySubmissionsPerDay, @DefaultValue("500") int noteMaxLength,
		@DefaultValue("20") int defaultPageSize, @DefaultValue("50") int maxPageSize, @DefaultValue("10") int shortlistPerGig,
		@DefaultValue("20") int shortlistActionsPerDay, @DefaultValue("200") int lapseBatchSize,
		@DefaultValue("30") int hiredContactDaysAfterGig) {
}
