package com.gigwork.core.identity.persistence;

import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

/**
 * Base of entities whose UUID is assigned by the application (NFR-33). Without it Spring Data cannot tell a new
 * entity from an existing one and would run a SELECT before every INSERT.
 */
@MappedSuperclass
abstract class AssignedIdEntity implements Persistable<UUID> {

	@Transient
	private boolean isNew = true;

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostPersist
	@PostLoad
	void markNotNew() {
		isNew = false;
	}

}
