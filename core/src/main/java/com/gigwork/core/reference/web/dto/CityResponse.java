package com.gigwork.core.reference.web.dto;

import java.util.UUID;

import com.gigwork.core.reference.CityInfo;

public record CityResponse(UUID id, String slug, String name) {

	public static CityResponse of(CityInfo c) {
		return new CityResponse(c.id(), c.slug(), c.name());
	}

}
