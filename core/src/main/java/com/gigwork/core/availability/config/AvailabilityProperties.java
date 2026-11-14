package com.gigwork.core.availability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Availability settings ({@code gigwork.availability.*}).
 *
 * @param defaultTimeZone used for all-day blocks and the calendar of a Talent with no saved location
 * @param maxUpcomingBlocks FR-7: busy blocks that have not ended yet, per Talent
 * @param maxBlockDays FR-7: longest busy block
 * @param maxCalendarDays longest range of one calendar request
 */
@ConfigurationProperties("gigwork.availability")
public record AvailabilityProperties(@DefaultValue("Asia/Kolkata") String defaultTimeZone,
		@DefaultValue("200") int maxUpcomingBlocks, @DefaultValue("90") int maxBlockDays,
		@DefaultValue("92") int maxCalendarDays) {
}
