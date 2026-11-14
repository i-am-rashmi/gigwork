package com.gigwork.core.reference.persistence;

import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only. The generated point column is not mapped: lat and lng carry the same values. */
@Entity
@Immutable
@Table(schema = "reference", name = "locality")
class Locality {

	@Id
	private UUID id;
	@Column(name = "source_id")
	private String sourceId;
	@Column(name = "city_id")
	private UUID cityId;
	@Column(name = "zone_id")
	private UUID zoneId;
	private String name;
	private String pin;
	private double lat;
	private double lng;
	private String status;

	protected Locality() {
	}

	UUID getId() {
		return id;
	}

	UUID getCityId() {
		return cityId;
	}

	UUID getZoneId() {
		return zoneId;
	}

	String getName() {
		return name;
	}

	String getPin() {
		return pin;
	}

	double getLat() {
		return lat;
	}

	double getLng() {
		return lng;
	}

	boolean isLive() {
		return "LIVE".equals(status);
	}

}
