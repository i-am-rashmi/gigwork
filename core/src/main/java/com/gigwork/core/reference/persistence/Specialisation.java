package com.gigwork.core.reference.persistence;

import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only: reference rows change only through migrations. */
@Entity
@Immutable
@Table(schema = "reference", name = "specialisation")
class Specialisation {

	@Id
	private UUID id;
	@Column(name = "category_id")
	private UUID categoryId;
	private String slug;
	private String name;
	private String status;
	@Column(name = "display_order")
	private int displayOrder;

	protected Specialisation() {
	}

	UUID getId() {
		return id;
	}

	UUID getCategoryId() {
		return categoryId;
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

	int getDisplayOrder() {
		return displayOrder;
	}

}
