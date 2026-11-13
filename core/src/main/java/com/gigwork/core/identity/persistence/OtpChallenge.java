package com.gigwork.core.identity.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Inserted through JPA; every later change (attempt counter, used_at) is a native statement. */
@Entity
@Table(schema = "identity", name = "otp_challenge")
class OtpChallenge extends AssignedIdEntity {

	@Id
	private UUID id;

	@Column(name = "target_hash", nullable = false)
	private byte[] targetHash;

	@Column(name = "code_hash", nullable = false)
	private byte[] codeHash;

	@Column(nullable = false)
	private String purpose;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private short attempts;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "delivery_failed_at")
	private Instant deliveryFailedAt;

	protected OtpChallenge() {
	}

	OtpChallenge(UUID id, byte[] targetHash, byte[] codeHash, Instant createdAt, Instant expiresAt) {
		this.id = id;
		this.targetHash = targetHash;
		this.codeHash = codeHash;
		this.purpose = "LOGIN";
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	@Override
	public UUID getId() {
		return id;
	}

}
