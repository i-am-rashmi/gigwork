package com.gigwork.core.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PortfolioLinkTest {

	private static final List<String> BLOCKED = List.of("bit.ly", "tinyurl.com", "t.co", "goo.gl", "rb.gy", "cutt.ly", "is.gd");

	private static PortfolioLink parse(String text) {
		return PortfolioLink.parse(text, BLOCKED);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"instagram.com/asha.photos|https://instagram.com/asha.photos|INSTAGRAM",
			"https://www.instagram.com/asha.photos/|https://www.instagram.com/asha.photos/|INSTAGRAM",
			"http://youtube.com/watch?v=abc|http://youtube.com/watch?v=abc|YOUTUBE",
			"youtu.be/abc123|https://youtu.be/abc123|YOUTUBE",
			"m.youtube.com/@asha|https://m.youtube.com/@asha|YOUTUBE",
			"behance.net/asha|https://behance.net/asha|BEHANCE",
			"vimeo.com/12345|https://vimeo.com/12345|VIMEO",
			"soundcloud.com/asha/track|https://soundcloud.com/asha/track|AUDIO",
			"open.spotify.com/artist/x|https://open.spotify.com/artist/x|AUDIO",
			"facebook.com/asha|https://facebook.com/asha|SOCIAL",
			"in.linkedin.com/in/asha|https://in.linkedin.com/in/asha|SOCIAL",
			"asha-photography.in|https://asha-photography.in|WEBSITE",
			"https://asha.example.com/work?x=1#top|https://asha.example.com/work?x=1|WEBSITE",
			"  EXAMPLE.COM/Portfolio  |https://example.com/Portfolio|WEBSITE",
			"//example.com|https://example.com|WEBSITE",
			"https://example.com/|https://example.com|WEBSITE",
			"https://example.com:443/a|https://example.com/a|WEBSITE",
			"https://notinstagram.com/x|https://notinstagram.com/x|WEBSITE",
			"https://xn--bcher-kva.example/x|https://xn--bcher-kva.example/x|WEBSITE" })
	void acceptsAndNormalisesAndDerivesTheType(String input, String expectedUrl, LinkType expectedType) {
		PortfolioLink link = parse(input);

		assertThat(link.url()).isEqualTo(expectedUrl);
		assertThat(link.type()).isEqualTo(expectedType);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "ftp://example.com/x", "javascript:alert(1)", "data:text/html,hi", "file:///etc/passwd",
			"http://localhost/x", "http://localhost:3000", "localhost", "http://127.0.0.1/x", "http://192.168.1.10", "http://[::1]/x",
			"https://10.0.0.1:8080", "example", "https://example", "https://user:pass@example.com", "https://user@example.com",
			"https://exa mple.com", "https://-bad.example.com", "https://example..com", "https://example.c", "https://example.123",
			"https://foo.local", "https://printer.internal/x", "https://example.com:8080/x", "https://" })
	void rejectsAddressesThatAreNotPublicWebDomains(String input) {
		assertThatThrownBy(() -> parse(input)).isInstanceOf(InvalidLinkException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "bit.ly/abc", "https://tinyurl.com/x", "t.co/abc", "goo.gl/maps/x", "rb.gy/x", "cutt.ly/x", "is.gd/x",
			"https://www.bit.ly/abc", "https://sub.t.co/abc" })
	void rejectsUrlShorteners(String input) {
		assertThatThrownBy(() -> parse(input)).isInstanceOf(InvalidLinkException.class).hasMessageContaining("Shortened");
	}

	@ParameterizedTest
	@ValueSource(strings = { "wa.me/919845012345", "https://wa.me/919845012345", "https://api.whatsapp.com/send?phone=91", "t.me/someone",
			"https://t.me/someone", "telegram.me/x", "tel:+919845012345", "mailto:me@example.com", "TEL:9845012345",
			"https://WWW.WA.ME/x" })
	void rejectsContactLinks(String input) {
		assertThatThrownBy(() -> parse(input)).isInstanceOf(InvalidLinkException.class).hasMessageContaining("Contact links");
	}

	@org.junit.jupiter.api.Test
	void aSuffixOfABlockedHostIsNotBlocked() {
		assertThat(parse("https://habit.ly/x").type()).isEqualTo(LinkType.WEBSITE); // not a subdomain of bit.ly
		assertThat(parse("https://about.co/x").url()).isEqualTo("https://about.co/x"); // not t.co
	}

	@org.junit.jupiter.api.Test
	void aBlockedListFromConfigurationIsUsed() {
		assertThat(PortfolioLink.parse("https://short.example/x", List.of()).url()).isEqualTo("https://short.example/x");
		assertThatThrownBy(() -> PortfolioLink.parse("https://short.example/x", List.of("short.example")))
				.isInstanceOf(InvalidLinkException.class);
	}

	@org.junit.jupiter.api.Test
	void rejectsALinkLongerThan2048Characters() {
		String longPath = "https://example.com/" + "a".repeat(2048);

		assertThatThrownBy(() -> parse(longPath)).isInstanceOf(InvalidLinkException.class).hasMessageContaining("2048");
		assertThat(parse("https://example.com/" + "a".repeat(2048 - "https://example.com/".length())).url()).hasSize(2048);
	}

	@org.junit.jupiter.api.Test
	void messagesNeverEchoTheLink() {
		assertThatThrownBy(() -> parse("https://secret-host-9845012345.example/x?token=abc#frag ment"))
				.isInstanceOf(InvalidLinkException.class).satisfies(e -> assertThat(e.getMessage()).doesNotContain("secret"));
	}

}
