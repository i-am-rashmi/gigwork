package com.gigwork.core.platform;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Finds contact details in free text (FR-6a): Indian mobile numbers, email addresses and contact links. Contact
 * details are shared only through the reveal flow, so profile text and names must not carry them. Shared by
 * Identity (first name) and Talent Profile (headline, bio, links), so it lives in the platform module.
 *
 * <p>It is a heuristic against honest mistakes and casual attempts, not a guarantee: numbers spelled out in words
 * or an address written as "name at gmail dot com" are not recognised.
 */
public final class ContactDetails {

	/** Hosts of contact links (chat and messaging links), without {@code www.}. */
	public static final List<String> CONTACT_HOSTS = List.of("wa.me", "api.whatsapp.com", "t.me", "telegram.me");

	/** 10 digits starting 6-9, optional +91 / 91 / 0 prefix, single spaces or dashes between digits. */
	private static final Pattern MOBILE = Pattern
			.compile("(?<![0-9])(?:(?:\\+\\s?91|91|0)[\\s-]*)?[6-9](?:[\\s-]?[0-9]){9}(?![0-9])");

	private static final Pattern EMAIL = Pattern
			.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}");

	private static final Pattern CONTACT_LINK = Pattern.compile(
			"(?i)(?<![A-Za-z0-9.-])(?:www\\.)?(?:wa\\.me|api\\.whatsapp\\.com|t\\.me|telegram\\.me)(?![A-Za-z0-9-])|\\b(?:tel|mailto):");

	private ContactDetails() {
	}

	/** Whether the text contains a phone number, an email address or a contact link. */
	public static boolean appearsIn(String text) {
		if (text == null || text.isEmpty()) {
			return false;
		}
		return MOBILE.matcher(text).find() || EMAIL.matcher(text).find() || CONTACT_LINK.matcher(text).find();
	}

	/** Whether {@code host} (any case, with or without {@code www.}) is a contact-link host. */
	public static boolean isContactHost(String host) {
		String h = host.toLowerCase(Locale.ROOT);
		if (h.startsWith("www.")) {
			h = h.substring(4);
		}
		return CONTACT_HOSTS.contains(h);
	}

}
