package com.gigwork.core.gigs;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read-only questions other modules may ask Gigs. Any status is returned: callers decide what a closed gig means. */
public interface GigLookup {

	/** The gig, or empty if it does not exist. */
	Optional<GigSummary> find(UUID gigId);

	/** Many gigs in one query, for lists (no N+1). Unknown IDs are simply absent from the result. */
	Map<UUID, GigSummary> findAll(Collection<UUID> gigIds);

}
