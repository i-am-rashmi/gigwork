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
	@jakarta.persistence.Column(name = "time_zone")
	private String timeZone;

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

	String getTimeZone() {
		return timeZone;
	}

	boolean isLive() {
		return "LIVE".equals(status);
	}

}
