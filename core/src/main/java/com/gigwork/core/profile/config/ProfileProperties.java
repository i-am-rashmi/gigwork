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
		@DefaultValue("10") int maxLinks, @DefaultValue Portfolio portfolio) {

	/** @param blockedHosts URL shorteners (and their subdomains) that are not accepted as portfolio links */
	public record Portfolio(@DefaultValue({ "bit.ly", "tinyurl.com", "t.co", "goo.gl", "rb.gy", "cutt.ly",
			"is.gd" }) List<String> blockedHosts) {
	}

}
