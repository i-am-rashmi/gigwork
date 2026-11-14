package com.gigwork.core.gigs;

import java.time.Instant;
import java.util.UUID;

/**
 * A gig was published and is OPEN. Carries what consumers need, so none has to read Gigs' tables (components and
 * flows, events). Written in the transaction that saved the gig.
 */
public record GigPublished(UUID gigId, UUID posterId, UUID categoryId, UUID specialisationId, String languageCode,
		String workMode, UUID cityId, UUID zoneId, Instant startAt, Instant endAt, Instant publishedAt) {
}
