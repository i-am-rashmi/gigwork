package com.gigwork.core.reference.web.dto;

import java.util.UUID;

import com.gigwork.core.reference.SpecialisationInfo;

public record SpecialisationResponse(UUID id, String slug, String name) {

	public static SpecialisationResponse of(SpecialisationInfo s) {
		return new SpecialisationResponse(s.id(), s.slug(), s.name());
	}

}
