package com.gigwork.core.hiring.application;

import java.time.Instant;
import java.util.UUID;

import com.gigwork.core.gigs.GigSummary;

/**
 * One entry of a Talent's Shortlisted tab (FR-26): the gig as it is now (closed gigs stay, with their status), the
 * Poster's first name, whether the Poster shares their contact, and the Talent's own consent. No contact details.
 */
public record ShortlistedItem(UUID applicationId, Instant shortlistedAt, GigSummary gig, String posterFirstName,
		boolean posterShares, boolean consented, Instant consentedAt) {
}
