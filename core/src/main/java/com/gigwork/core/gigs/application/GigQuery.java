package com.gigwork.core.gigs.application;

import java.util.List;
import java.util.UUID;

/**
 * A request to list open gigs. Lists may be empty (no filter on that); {@code cursor} and {@code size} may be null
 * (first page, default size).
 */
public record GigQuery(List<UUID> categoryIds, UUID cityId, List<UUID> zoneIds, String cursor, Integer size) {
}
