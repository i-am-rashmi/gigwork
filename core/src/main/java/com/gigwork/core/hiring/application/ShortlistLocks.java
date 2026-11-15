package com.gigwork.core.hiring.application;

import java.util.UUID;

/**
 * The two locks that serialise shortlist changes: the Poster's (the 24-hour limit spans their gigs) and then the
 * gig's (the limit of Talent shortlisted at once). Always in that order. Its own type so a test can take them away
 * and show that the limits then break. Call it inside a transaction.
 */
interface ShortlistLocks {

	void lock(UUID posterId, UUID gigId);

}
