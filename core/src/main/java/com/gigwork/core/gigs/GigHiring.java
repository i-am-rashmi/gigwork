package com.gigwork.core.gigs;

import java.time.Instant;
import java.util.UUID;

/**
 * What Hiring may change about a gig (FR-27, FR-27a). Each call is one conditional statement and says whether a row
 * changed; Gigs never calls Hiring. Call these inside the caller's transaction: they take part in it, so a refusal
 * further on rolls them back (confirming a hire either fills the gig, books the Talent and confirms, or nothing).
 */
public interface GigHiring {

	/** Marks the gig as waiting for a hire to be confirmed (editing is then refused). False unless OPEN and not started. */
	boolean holdForHire(UUID gigId, Instant now);

	/** Clears the mark. False unless the gig is OPEN (a closed gig has no mark). */
	boolean releaseHold(UUID gigId);

	/** OPEN to FILLED (also clears the mark). False unless OPEN and not started. */
	boolean fill(UUID gigId, Instant now);

	/** FILLED back to OPEN while the gig has not started, otherwise to EXPIRED (then {@link GigExpired} is published). */
	Outcome reopenOrExpire(UUID gigId, Instant now);

	enum Outcome {
		REOPENED, EXPIRED, NOT_FILLED
	}

}
