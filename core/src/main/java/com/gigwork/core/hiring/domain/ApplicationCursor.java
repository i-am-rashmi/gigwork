package com.gigwork.core.hiring.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * The opaque cursor of an application list: the last application of a page as {@code (applied at, ID)}. Same scheme
 * as the gig list's cursor: encoded, not signed (both values are already in the page); a damaged one is a 400.
 */
public record ApplicationCursor(Instant appliedAt, UUID id) {

	private static final String VERSION = "1";

	public String encode() {
		long micros = ChronoUnit.MICROS.between(Instant.EPOCH, appliedAt);
		String text = VERSION + "." + micros + "." + id;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}

	public static ApplicationCursor decode(String cursor) {
		try {
			String text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			String[] parts = text.split("\\.", -1);
			if (parts.length != 3 || !parts[0].equals(VERSION)) {
				throw new InvalidApplicationException("That page cursor is not valid.");
			}
			return new ApplicationCursor(Instant.EPOCH.plus(Long.parseLong(parts[1]), ChronoUnit.MICROS),
					UUID.fromString(parts[2]));
		}
		catch (IllegalArgumentException | ArithmeticException | java.time.DateTimeException e) {
			throw new InvalidApplicationException("That page cursor is not valid.");
		}
	}

}
