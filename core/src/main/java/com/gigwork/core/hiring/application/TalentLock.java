package com.gigwork.core.hiring.application;

import java.util.UUID;

/**
 * The per-Talent lock that serialises one Talent's submissions, so the count and the insert cannot interleave with
 * another submission of the same Talent. Its own type so a test can take it away and show that the limit then
 * breaks. Call it inside a transaction.
 */
interface TalentLock {

	void lock(UUID talentId);

}
