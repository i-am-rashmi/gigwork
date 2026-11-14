package com.gigwork.core.reference;

import java.util.UUID;

/** @param timeZone IANA name, e.g. {@code Asia/Kolkata}: how dates in this city are read (all-day busy blocks) */
public record CityInfo(UUID id, String slug, String name, String timeZone) {
}
