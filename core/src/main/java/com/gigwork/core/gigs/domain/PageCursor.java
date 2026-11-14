package com.gigwork.core.gigs.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * The opaque cursor of a gig list: the last gig of a page as {@code (published at, ID)}. Clients pass it back as
 * they got it. It carries nothing secret (both values are already in the page), so it is encoded, not signed; a
 * damaged or invented one is refused with a 400.
 */
public record PageCursor(Instant publishedAt, UUID id) {

	private static final String VERSION = "1";

	public String encode() {
		long micros = ChronoUnit.MICROS.between(Instant.EPOCH, publishedAt);
		String text = VERSION + "." + micros + "." + id;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}

	public static PageCursor decode(String cursor) {
		try {
			String text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			String[] parts = text.split("\\.", -1);
			if (parts.length != 3 || !parts[0].equals(VERSION)) {
				throw new InvalidGigException("That page cursor is not valid.");
			}
			return new PageCursor(Instant.EPOCH.plus(Long.parseLong(parts[1]), ChronoUnit.MICROS), UUID.fromString(parts[2]));
		}
		catch (IllegalArgumentException | ArithmeticException | java.time.DateTimeException e) {
			throw new InvalidGigException("That page cursor is not valid.");
		}
	}

}
