package com.gigwork.core.hiring.application;

import java.util.UUID;

/** What a Poster sees of a shortlist right after a change; never contact details. */
public record ShortlistState(UUID applicationId, boolean shortlisted, boolean posterShares, boolean talentConsented) {
}
