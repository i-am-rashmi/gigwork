package com.gigwork.core.hiring.persistence;

import java.time.Instant;
import java.util.UUID;

import com.gigwork.core.hiring.HireSide;

/** A hire as stored. */
public record StoredHire(UUID id, UUID gigId, UUID applicationId, UUID talentId, UUID posterId, HireSide startedBy,
		String status, Instant startedAt, Instant expiresAt, Instant confirmedAt, Instant endedAt, HireSide cancelledBy,
		String cancelReason, UUID bookingId) {

	/** The side {@code account} is on, if it is on either. */
	public java.util.Optional<HireSide> sideOf(UUID account) {
		if (account.equals(posterId)) {
			return java.util.Optional.of(HireSide.POSTER);
		}
		return account.equals(talentId) ? java.util.Optional.of(HireSide.TALENT) : java.util.Optional.empty();
	}

	/** The other side of the hire from {@code side}. */
	public static HireSide other(HireSide side) {
		return side == HireSide.POSTER ? HireSide.TALENT : HireSide.POSTER;
	}

}
