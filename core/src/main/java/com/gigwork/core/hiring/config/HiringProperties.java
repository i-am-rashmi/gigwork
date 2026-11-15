package com.gigwork.core.hiring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Hiring settings ({@code gigwork.hiring.*}).
 *
 * @param applySubmissionsPerDay NFR-39: the abuse limit, submissions per Talent in any rolling 24 hours
 * @param noteMaxLength FR-23: longest application note, in characters
 * @param defaultPageSize applications per page when the request does not say
 * @param maxPageSize most applications per page
 */
@ConfigurationProperties("gigwork.hiring")
public record HiringProperties(@DefaultValue("30") int applySubmissionsPerDay, @DefaultValue("500") int noteMaxLength,
		@DefaultValue("20") int defaultPageSize, @DefaultValue("50") int maxPageSize) {
}
