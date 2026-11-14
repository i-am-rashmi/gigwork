package com.gigwork.core.profile.domain;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.gigwork.core.platform.ContactDetails;

/**
 * A portfolio link, normalised and checked (FR-6). It is only ever a string we store and show: the address is
 * never fetched. {@code url} is the normalised form (lower-case scheme and host, no fragment, no default port, no
 * trailing slash on an empty path), used for duplicate checks.
 */
public record PortfolioLink(String url, LinkType type) {

	public static final int MAX_LENGTH = 2048;

	private static final Pattern HAS_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://.*");
	private static final Pattern CONTACT_SCHEME = Pattern.compile("(?i)^(?:tel|mailto|sms|callto|sip|whatsapp|skype):.*");
	private static final Pattern DANGEROUS_SCHEME = Pattern.compile("(?i)^(?:javascript|data|file|ftp|vbscript|blob|about):.*");
	private static final Pattern IPV4 = Pattern.compile("^[0-9]+(?:\\.[0-9]+){3}$");
	private static final Pattern LABEL = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
	private static final Pattern TLD = Pattern.compile("^(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})$");
	private static final List<String> LOCAL_TLDS = List.of("local", "localhost", "internal", "intranet", "lan", "home", "corp");

	/**
	 * @param blockedHosts hosts (and their subdomains) that are not accepted, such as URL shorteners
	 * @throws InvalidLinkException with a message for the user if the link is not acceptable
	 */
	public static PortfolioLink parse(String input, List<String> blockedHosts) {
		if (input == null || input.isBlank()) {
			throw new InvalidLinkException("A portfolio link is empty.");
		}
		String text = input.strip();
		if (CONTACT_SCHEME.matcher(text).matches()) {
			throw new InvalidLinkException("Contact links such as phone, email and chat links can't be portfolio links.");
		}
		if (DANGEROUS_SCHEME.matcher(text).matches()) {
			throw new InvalidLinkException("Portfolio links must start with http:// or https://.");
		}
		if (text.startsWith("//")) {
			text = text.substring(2);
		}
		if (HAS_SCHEME.matcher(text).matches()) {
			String scheme = text.substring(0, text.indexOf("://")).toLowerCase(Locale.ROOT);
			if (!scheme.equals("http") && !scheme.equals("https")) {
				throw new InvalidLinkException("Portfolio links must start with http:// or https://.");
			}
		}
		else {
			text = "https://" + text;
		}
		if (text.length() > MAX_LENGTH) {
			throw new InvalidLinkException("A portfolio link is longer than " + MAX_LENGTH + " characters.");
		}

		URI uri;
		try {
			uri = new URI(text);
		}
		catch (URISyntaxException e) {
			throw new InvalidLinkException("A portfolio link is not a valid web address.");
		}
		String host = uri.getHost();
		if (host == null || uri.getUserInfo() != null) {
			throw new InvalidLinkException("A portfolio link is not a valid web address.");
		}
		String asciiHost = asciiDomain(host);
		if (ContactDetails.isContactHost(asciiHost)) {
			throw new InvalidLinkException("Contact links such as phone, email and chat links can't be portfolio links.");
		}
		if (isBlocked(asciiHost, blockedHosts)) {
			throw new InvalidLinkException("Shortened links aren't accepted; use the full address.");
		}
		int port = uri.getPort();
		if (port != -1 && port != 80 && port != 443) {
			throw new InvalidLinkException("A portfolio link can't use a custom port.");
		}

		String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
		String path = uri.getRawPath() == null || uri.getRawPath().equals("/") ? "" : uri.getRawPath();
		String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
		String normalised = scheme + "://" + asciiHost + path + query;
		if (normalised.length() > MAX_LENGTH) {
			throw new InvalidLinkException("A portfolio link is longer than " + MAX_LENGTH + " characters.");
		}
		return new PortfolioLink(normalised, typeOf(asciiHost));
	}

	/** A real domain name: lower case ASCII (punycode for IDNs), at least two labels, a letters-only top-level domain, not an IP address and not local. */
	private static String asciiDomain(String rawHost) {
		String host = rawHost.toLowerCase(Locale.ROOT);
		if (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		if (host.startsWith("[") || IPV4.matcher(host).matches()) {
			throw new InvalidLinkException("A portfolio link must use a domain name, not an IP address.");
		}
		String ascii;
		try {
			ascii = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
		}
		catch (IllegalArgumentException e) {
			throw new InvalidLinkException("A portfolio link is not a valid web address.");
		}
		String[] labels = ascii.split("\\.", -1);
		if (labels.length < 2 || ascii.equals("localhost")) {
			throw new InvalidLinkException("A portfolio link must use a public domain name.");
		}
		for (String label : labels) {
			if (!LABEL.matcher(label).matches()) {
				throw new InvalidLinkException("A portfolio link is not a valid web address.");
			}
		}
		String tld = labels[labels.length - 1];
		if (!TLD.matcher(tld).matches() || LOCAL_TLDS.contains(tld)) {
			throw new InvalidLinkException("A portfolio link must use a public domain name.");
		}
		return ascii;
	}

	private static boolean isBlocked(String host, List<String> blocked) {
		return blocked.stream().map(h -> h.toLowerCase(Locale.ROOT)).anyMatch(b -> sameOrSubdomain(host, b));
	}

	private static boolean sameOrSubdomain(String host, String domain) {
		return host.equals(domain) || host.endsWith("." + domain);
	}

	static LinkType typeOf(String host) {
		if (sameOrSubdomain(host, "instagram.com")) {
			return LinkType.INSTAGRAM;
		}
		if (sameOrSubdomain(host, "youtube.com") || sameOrSubdomain(host, "youtu.be")) {
			return LinkType.YOUTUBE;
		}
		if (sameOrSubdomain(host, "behance.net")) {
			return LinkType.BEHANCE;
		}
		if (sameOrSubdomain(host, "vimeo.com")) {
			return LinkType.VIMEO;
		}
		if (sameOrSubdomain(host, "soundcloud.com") || sameOrSubdomain(host, "spotify.com")) {
			return LinkType.AUDIO;
		}
		if (sameOrSubdomain(host, "facebook.com") || sameOrSubdomain(host, "linkedin.com")) {
			return LinkType.SOCIAL;
		}
		return LinkType.WEBSITE;
	}

}
