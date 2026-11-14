package com.gigwork.core.reference;

import java.util.UUID;

/** A zone of a city (the boundary itself stays inside the module). */
public record ZoneInfo(UUID id, UUID cityId, String name) {
}
