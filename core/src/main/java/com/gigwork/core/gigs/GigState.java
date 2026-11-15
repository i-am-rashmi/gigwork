package com.gigwork.core.gigs;

/** Where a gig is in its lifecycle, as other modules may see it (data model section 5). */
public enum GigState {
	OPEN, FILLED, CANCELLED, EXPIRED
}
