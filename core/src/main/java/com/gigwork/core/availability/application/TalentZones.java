package com.gigwork.core.availability.application;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.gigwork.core.availability.config.AvailabilityProperties;
import com.gigwork.core.profile.TalentProfileSummary;
import com.gigwork.core.profile.TalentProfiles;
import com.gigwork.core.reference.CityInfo;
import com.gigwork.core.reference.ReferenceData;

/**
 * The time zone a Talent's dates are read in: their city's, or the configured default while they have no saved
 * location (or the city is no longer live).
 */
@Component
class TalentZones {

	/** @param hasProfile whether the account has a profile of any status */
	record TalentZone(boolean hasProfile, ZoneId zone) {
	}

	private final TalentProfiles profiles;
	private final ReferenceData reference;
	private final ZoneId defaultZone;

	TalentZones(TalentProfiles profiles, ReferenceData reference, AvailabilityProperties properties) {
		this.profiles = profiles;
		this.reference = reference;
		try {
			this.defaultZone = ZoneId.of(properties.defaultTimeZone());
		}
		catch (DateTimeException e) {
			throw new IllegalConfigurationException("gigwork.availability.default-time-zone is not a time zone", e);
		}
	}

	TalentZone of(UUID accountId) {
		Optional<TalentProfileSummary> profile = profiles.summary(accountId);
		ZoneId zone = profile.map(TalentProfileSummary::cityId).flatMap(reference::city).map(CityInfo::timeZone)
				.map(ZoneId::of).orElse(defaultZone);
		return new TalentZone(profile.isPresent(), zone);
	}

	/** Startup refuses an unusable default zone instead of failing on the first request. */
	static class IllegalConfigurationException extends RuntimeException {

		IllegalConfigurationException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
