package com.gigwork.core.reference.persistence;

import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only: reference rows change only through migrations. */
@Entity
@Immutable
@Table(schema = "reference", name = "city")
class City {

	@Id
	private UUID id;
	private String slug;
	private String name;
	private String status;

	protected City() {
	}

	UUID getId() {
		return id;
	}

	String getSlug() {
		return slug;
	}

	String getName() {
		return name;
	}

	boolean isLive() {
		return "LIVE".equals(status);
	}

}
