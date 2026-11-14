package com.gigwork.core.reference.persistence;

import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only. The boundary column is deliberately not mapped: only PostGIS queries use it. */
@Entity
@Immutable
@Table(schema = "reference", name = "zone")
class Zone {

	@Id
	private UUID id;
	@Column(name = "city_id")
	private UUID cityId;
	private String name;
	private String status;

	protected Zone() {
	}

	UUID getId() {
		return id;
	}

	UUID getCityId() {
		return cityId;
	}

	String getName() {
		return name;
	}

	boolean isLive() {
		return "LIVE".equals(status);
	}

}
