package com.gigwork.core.reference.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reference data settings ({@code gigwork.reference.*}).
 *
 * @param location how a chosen map point is accepted and labelled (used by gigs; profiles have their own copy)
 */
@ConfigurationProperties("gigwork.reference")
public record ReferenceProperties(@DefaultValue Location location) {

	/**
	 * @param maxDistanceFromZoneKm a map point farther than this from the nearest live zone is refused
	 * @param labelRadiusMeters a map point is labelled with the nearest locality within this distance, else the zone
	 */
	public record Location(@DefaultValue("50") double maxDistanceFromZoneKm, @DefaultValue("2000") double labelRadiusMeters) {
	}

}
