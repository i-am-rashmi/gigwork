package com.gigwork.core.gigs.persistence;

import java.time.Instant;
import java.util.UUID;

/** The last gig of a page: the next page is every gig that sorts after it (newest first, ties by ID). */
public record Keyset(Instant publishedAt, UUID id) {
}
