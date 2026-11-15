package com.gigwork.core.platform.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Event resubmission settings ({@code gigwork.platform.events.*}).
 *
 * @param resubmitOlderThan an event whose listener has not completed after this long is delivered again
 */
@ConfigurationProperties("gigwork.platform.events")
public record EventResubmissionProperties(@DefaultValue("2m") Duration resubmitOlderThan) {
}
