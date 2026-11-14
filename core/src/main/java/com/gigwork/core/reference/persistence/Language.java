package com.gigwork.core.reference.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only: reference rows change only through migrations. */
@Entity
@Immutable
@Table(schema = "reference", name = "language")
class Language {

	@Id
	private String code;
	private String name;
	private String status;
	@Column(name = "display_order")
	private int displayOrder;

	protected Language() {
	}

	String getCode() {
		return code;
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
