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
@Table(schema = "reference", name = "category")
class Category {

	@Id
	private UUID id;
	private String slug;
	private String name;
	@Column(name = "work_mode")
	private String workMode;
	@Column(name = "language_relevant")
	private boolean languageRelevant;
	private String status;
	@Column(name = "display_order")
	private int displayOrder;

	protected Category() {
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

	String getWorkMode() {
		return workMode;
	}

	boolean isLanguageRelevant() {
		return languageRelevant;
	}

	boolean isLive() {
		return "LIVE".equals(status);
	}

	int getDisplayOrder() {
		return displayOrder;
	}

}
