package com.gigwork.core.reference;

import java.util.UUID;

/**
 * The city and zone a point belongs to. {@code insideZone} is false when the point lies outside every zone and the
 * nearest zone was used instead (FR-5).
 *
 * @param distanceToZoneMeters 0 inside a zone, else the distance in metres to the nearest zone's boundary
 */
public record AreaResolution(UUID cityId, UUID zoneId, boolean insideZone, double distanceToZoneMeters) {
}
