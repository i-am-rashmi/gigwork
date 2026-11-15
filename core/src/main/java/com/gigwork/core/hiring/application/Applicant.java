package com.gigwork.core.hiring.application;

import java.time.Instant;
import java.util.UUID;

import com.gigwork.core.profile.ApplicantProfile;

/**
 * An active application as the gig's Poster sees it (FR-24). No contact details, no busy warning flag, nothing of
 * the Talent beyond the profile a logged-in user sees. {@code profile} is null only if the Talent has no ACTIVE or
 * HIDDEN profile any more, which cannot happen after applying.
 */
public record Applicant(UUID id, UUID talentId, String note, Instant appliedAt, ApplicantProfile profile) {
}
