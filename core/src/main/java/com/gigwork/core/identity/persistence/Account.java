package com.gigwork.core.identity.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "identity", name = "account")
class Account extends AssignedIdEntity {

	static final String ACTIVE = "ACTIVE";

	@Id
	private UUID id;

	@Column(nullable = false)
	private String status;

	@Column(name = "first_name")
	private String firstName;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Account() {
	}

	Account(UUID id, Instant createdAt) {
		this.id = id;
		this.status = ACTIVE;
		this.createdAt = createdAt;
	}

	@Override
	public UUID getId() {
		return id;
	}

	String getStatus() {
		return status;
	}

	String getFirstName() {
		return firstName;
	}

	void setFirstName(String firstName) {
		this.firstName = firstName;
	}

}
