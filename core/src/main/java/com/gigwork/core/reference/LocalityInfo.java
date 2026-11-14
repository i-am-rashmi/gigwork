package com.gigwork.core.reference;

import java.util.UUID;

/**
 * A place name people can type, with its centre point. Only a search shortcut: real locations are coordinates.
 * Names are not unique (the same name can occur in several places of a city), so use {@code id}. {@code pin} is
 * null when unknown.
 */
public record LocalityInfo(UUID id, UUID cityId, UUID zoneId, String name, String pin, double lat, double lng) {
}
