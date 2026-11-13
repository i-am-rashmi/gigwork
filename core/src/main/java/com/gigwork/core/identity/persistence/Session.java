package com.gigwork.core.identity.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Inserted through JPA; the refresh-token rotation is a native statement. */
@Entity
@Table(schema = "identity", name = "session")
class Session extends AssignedIdEntity {

	@Id
	private UUID id;

	@Column(name = "account_id", nullable = false)
	private UUID accountId;

	@Column(name = "refresh_token_hash", nullable = false)
	private byte[] refreshTokenHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_used_at", nullable = false)
	private Instant lastUsedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected Session() {
	}

	Session(UUID id, UUID accountId, byte[] refreshTokenHash, Instant now) {
		this.id = id;
		this.accountId = accountId;
		this.refreshTokenHash = refreshTokenHash;
		this.createdAt = now;
		this.lastUsedAt = now;
	}

	@Override
	public UUID getId() {
		return id;
	}

}
