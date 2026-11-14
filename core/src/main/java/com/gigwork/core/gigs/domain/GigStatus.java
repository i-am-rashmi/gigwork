package com.gigwork.core.gigs.domain;

/** The gig lifecycle (data model section 5). Only Gigs changes it, always with a conditional update. */
public enum GigStatus {
	OPEN, FILLED, CANCELLED, EXPIRED
}
