package com.gigwork.core.gigs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Gig settings ({@code gigwork.gigs.*}).
 *
 * @param maxMonthsAhead FR-16: a gig can start at most this far ahead (12 months is "1 year")
 * @param maxLengthDays FR-16: longest gig
 * @param defaultPageSize gigs per page when the request does not say
 * @param maxPageSize most gigs per page
 * @param expiryBatchSize ADR-0010: open gigs expired per transaction by the expiry job
 */
@ConfigurationProperties("gigwork.gigs")
public record GigProperties(@DefaultValue("12") int maxMonthsAhead, @DefaultValue("14") int maxLengthDays,
		@DefaultValue("20") int defaultPageSize, @DefaultValue("50") int maxPageSize,
		@DefaultValue("500") int expiryBatchSize) {
}
