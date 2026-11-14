package com.gigwork.core.profile.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Talent profile settings ({@code gigwork.profile.*}).
 *
 * @param maxCategories FR-4: categories per Talent
 * @param maxSpecialisations specialisations per Talent, in total
 * @param maxLinks FR-6: portfolio links per Talent
 */
@ConfigurationProperties("gigwork.profile")
public record ProfileProperties(@DefaultValue("5") int maxCategories, @DefaultValue("10") int maxSpecialisations,
		@DefaultValue("10") int maxLinks, @DefaultValue Location location, @DefaultValue Portfolio portfolio) {

	/**
	 * @param maxDistanceFromZoneKm FR-5: a map point farther than this from the nearest live zone is rejected
	 * @param labelRadiusMeters a map point is labelled with the nearest locality within this distance, else the zone
	 */
	public record Location(@DefaultValue("50") double maxDistanceFromZoneKm, @DefaultValue("2000") double labelRadiusMeters) {
	}

	/** @param blockedHosts URL shorteners (and their subdomains) that are not accepted as portfolio links */
	public record Portfolio(@DefaultValue({ "bit.ly", "tinyurl.com", "t.co", "goo.gl", "rb.gy", "cutt.ly",
			"is.gd" }) List<String> blockedHosts) {
	}

}
