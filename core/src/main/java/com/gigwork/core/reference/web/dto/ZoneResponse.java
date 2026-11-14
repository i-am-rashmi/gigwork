package com.gigwork.core.reference.web.dto;

import java.util.UUID;

import com.gigwork.core.reference.ZoneInfo;

/** A zone's id and name; the boundary is not exposed. */
public record ZoneResponse(UUID id, String name) {

	public static ZoneResponse of(ZoneInfo z) {
		return new ZoneResponse(z.id(), z.name());
	}

}
