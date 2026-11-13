package com.gigwork.core.identity.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read-only mapping of the verified mobile number of an account. It is written only by the conflict-tolerant native
 * insert in {@link AccountJpaRepository}, never by load-modify-save.
 */
@Entity
@Table(schema = "identity", name = "mobile_identity")
class MobileIdentity extends AssignedIdEntity {

	@Id
	@Column(name = "account_id")
	private UUID accountId;

	@Column(nullable = false)
	private String mobile;

	@Column(name = "mobile_hash", nullable = false)
	private byte[] mobileHash;

	@Column(name = "verified_at", nullable = false)
	private Instant verifiedAt;

	protected MobileIdentity() {
	}

	@Override
	public UUID getId() {
		return accountId;
	}

}
