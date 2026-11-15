package com.gigwork.core.hiring.application;

import java.util.UUID;

/**
 * The lock that serialises one Poster's shortlist changes. The 24-hour limit spans their gigs, and because a gig has
 * one Poster it also covers the count of Talent shortlisted per gig. Its own type so a test can take it away and
 * show that the limits then break. Call it inside a transaction.
 */
interface ShortlistLocks {

	void lock(UUID posterId);

}
