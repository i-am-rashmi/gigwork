package com.gigwork.core.reference.web.dto;

import java.util.UUID;

import com.gigwork.core.reference.LocalityInfo;

/**
 * A place name with its centre point (reference data, not anyone's location). Names are not unique.
 *
 * @param pin null when unknown
 */
public record LocalityResponse(String name, String pin, double lat, double lng, UUID zoneId) {

	public static LocalityResponse of(LocalityInfo l) {
		return new LocalityResponse(l.name(), l.pin(), l.lat(), l.lng(), l.zoneId());
	}

}
