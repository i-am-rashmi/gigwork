package com.gigwork.core.profile.application;

import java.util.UUID;

/**
 * The location section (FR-5): exactly one of {@code localityId} or the point ({@code lat}, {@code lng}).
 */
public record LocationCommand(UUID localityId, Double lat, Double lng, int serviceRadiusKm, boolean willingToTravel) {
}
