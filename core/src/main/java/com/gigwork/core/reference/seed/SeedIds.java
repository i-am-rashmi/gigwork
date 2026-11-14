package com.gigwork.core.reference.seed;

import java.util.UUID;

/**
 * The deterministic IDs of reference rows. The namespace and the name formats are part of the data: changing
 * either would give every row a new ID, so they must never change. Each name starts with its kind so keys of
 * different kinds cannot collide. Languages need none: their ISO 639 code is their key.
 */
public final class SeedIds {

	/** Fixed forever. */
	static final UUID NAMESPACE = UUID.fromString("ca6d29e2-6222-4d95-a77c-713f92e4a3a4");

	private SeedIds() {
	}

	public static UUID category(String slug) {
		return UuidV5.of(NAMESPACE, "category:" + slug);
	}

	public static UUID specialisation(String categorySlug, String slug) {
		return UuidV5.of(NAMESPACE, "specialisation:" + categorySlug + "/" + slug);
	}

	public static UUID city(String slug) {
		return UuidV5.of(NAMESPACE, "city:" + slug);
	}

	public static UUID zone(String citySlug, String zoneName) {
		return UuidV5.of(NAMESPACE, "zone:" + citySlug + "/" + zoneName);
	}

	public static UUID locality(String citySlug, String sourceId) {
		return UuidV5.of(NAMESPACE, "locality:" + citySlug + "/" + sourceId);
	}

}
