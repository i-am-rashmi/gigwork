package com.gigwork.core.reference;

import java.util.UUID;

/** A second-level entry of a category. {@code live} is false once retired. */
public record SpecialisationInfo(UUID id, UUID categoryId, String slug, String name, boolean live, int displayOrder) {
}
