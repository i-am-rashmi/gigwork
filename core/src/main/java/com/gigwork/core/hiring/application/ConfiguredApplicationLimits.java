package com.gigwork.core.hiring.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.gigwork.core.hiring.config.HiringProperties;

/** The same configured limit for everyone (NFR-39). */
@Component
class ConfiguredApplicationLimits implements ApplicationLimits {

	private final HiringProperties properties;

	ConfiguredApplicationLimits(HiringProperties properties) {
		this.properties = properties;
	}

	@Override
	public int perRollingDay(UUID talentId) {
		return properties.applySubmissionsPerDay();
	}

}
