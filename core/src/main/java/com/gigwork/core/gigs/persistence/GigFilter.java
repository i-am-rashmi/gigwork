package com.gigwork.core.gigs.persistence;

import java.util.List;
import java.util.UUID;

/**
 * Which open gigs to list (FR-9, FR-11 in Phase 1). An empty list means "no filter on that". When any location
 * criterion is given, a remote gig always matches it (a remote gig is available anywhere); the categories still apply.
 *
 * @param cityIds the cities of the wanted on-site gigs; empty = no location filter (unless zones are given)
 * @param zoneIds the wanted zones within them
 */
public record GigFilter(List<UUID> categoryIds, List<UUID> cityIds, List<UUID> zoneIds) {

	public static final GigFilter NONE = new GigFilter(List.of(), List.of(), List.of());

	public boolean hasLocation() {
		return !cityIds.isEmpty() || !zoneIds.isEmpty();
	}

}
