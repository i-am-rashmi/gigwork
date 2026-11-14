package com.gigwork.core.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContactDetailsTest {

	@ParameterizedTest
	@ValueSource(strings = { "Call 9845012345", "98450 12345", "98450-12345", "9845 012 345", "+91-9845012345",
			"+91 98450 12345", "+919845012345", "919845012345", "09845012345", "0 9845012345",
			"reach me on 7012345678 anytime", "My whatsapp: 6 9845 01234", "a@b.co", "mail me at john.doe+gigs@example.com",
			"x@sub.example.in", "Chat at wa.me/919845012345", "https://api.whatsapp.com/send?phone=1", "t.me/someone",
			"telegram.me/abc", "WWW.WA.ME/xyz", "tel:+919845012345", "mailto:me@x.com" })
	void detectsContactDetails(String text) {
		assertThat(ContactDetails.appearsIn(text)).as(text).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Since 2015, 300+ weddings", "Over 1000 events in 2015-2020", "Rates from 8000 to 25000",
			"Studio since 2012 with 5 photographers and 12 assistants", "Booking opens 1st January 2026",
			"4.8 rating from 120 reviews", "Shot 40000+ photos", "Based in HSR Layout, Sector 2", "Call me maybe",
			"Plan B: backup gear, 2 cameras", "Fluent in Hindi, Kannada & English", "5 years 10 months", "",
			"ant.meals and artist.mehta are not links", "Instagram: @mehndi_by_asha", "0000 1111 2222",
			"1234567890", "PIN 560038" })
	void acceptsOrdinaryText(String text) {
		assertThat(ContactDetails.appearsIn(text)).as(text).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "wa.me", "WA.ME", "www.t.me", "api.whatsapp.com", "telegram.me" })
	void recognisesContactHosts(String host) {
		assertThat(ContactDetails.isContactHost(host)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "instagram.com", "me.com", "whatsapp.com", "notwa.me" })
	void otherHostsAreNotContactHosts(String host) {
		assertThat(ContactDetails.isContactHost(host)).isFalse();
	}

	@org.junit.jupiter.api.Test
	void nullIsClean() {
		assertThat(ContactDetails.appearsIn(null)).isFalse();
	}

}
