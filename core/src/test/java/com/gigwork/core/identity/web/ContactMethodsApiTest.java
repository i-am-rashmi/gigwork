package com.gigwork.core.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gigwork.core.identity.ContactCard;
import com.gigwork.core.identity.ContactMethods;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;

/** The contact methods a user shares (FR-2, minimal) and the card Hiring fetches (FR-26). */
@IdentityIntegrationTest
class ContactMethodsApiTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	TestAccounts accounts;
	@Autowired
	ContactMethods contactMethods;

	private ResultActions save(TestAccount account, String body) throws Exception {
		var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/v1/me/contact-methods").contentType(MediaType.APPLICATION_JSON).content(body);
		if (account != null) {
			request.header("Authorization", account.header());
		}
		return mvc.perform(request);
	}

	private static String body(boolean whatsapp, boolean call, boolean email) {
		return "{\"whatsapp\":" + whatsapp + ",\"call\":" + call + ",\"email\":" + email + "}";
	}

	@Test
	void noMethodsAreSharedUntilTheUserChooses() throws Exception {
		TestAccount account = accounts.create();

		mvc.perform(get("/v1/me/contact-methods").header("Authorization", account.header())).andExpect(status().isOk())
				.andExpect(jsonPath("$.whatsapp").value(false)).andExpect(jsonPath("$.call").value(false))
				.andExpect(jsonPath("$.email").value(false));
		assertThat(contactMethods.hasContactMethod(account.id())).isFalse();
		assertThat(contactMethods.contactCard(account.id())).isEmpty();
	}

	@Test
	void aUserChoosesWhatsappAndCallAndCanChangeTheirMind() throws Exception {
		TestAccount account = accounts.create();

		save(account, body(true, true, false)).andExpect(status().isOk()).andExpect(jsonPath("$.whatsapp").value(true))
				.andExpect(jsonPath("$.call").value(true));
		mvc.perform(get("/v1/me/contact-methods").header("Authorization", account.header()))
				.andExpect(jsonPath("$.whatsapp").value(true)).andExpect(jsonPath("$.call").value(true));
		save(account, body(false, true, false)).andExpect(status().isOk());
		mvc.perform(get("/v1/me/contact-methods").header("Authorization", account.header()))
				.andExpect(jsonPath("$.whatsapp").value(false)).andExpect(jsonPath("$.call").value(true));
		save(account, body(false, false, false)).andExpect(status().isOk());
		assertThat(contactMethods.hasContactMethod(account.id())).isFalse();
	}

	@Test
	void theResponseNeverContainsTheNumber() throws Exception {
		TestAccount account = accounts.create();
		String mobile = accounts.mobileOf(account.id());

		save(account, body(true, true, false)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(mobile.substring(3)))));
		mvc.perform(get("/v1/me/contact-methods").header("Authorization", account.header()))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(mobile.substring(3)))));
	}

	@Test
	void emailIsRefusedUntilEmailVerificationExists() throws Exception {
		TestAccount account = accounts.create();

		save(account, body(true, false, true)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Email can be shared once email verification is available."));
		mvc.perform(get("/v1/me/contact-methods").header("Authorization", account.header()))
				.andExpect(jsonPath("$.whatsapp").value(false)); // nothing was saved
	}

	@Test
	void everyValueMustBeGiven() throws Exception {
		TestAccount account = accounts.create();

		save(account, "{\"whatsapp\":true}").andExpect(status().isBadRequest());
		save(account, "{}").andExpect(status().isBadRequest());
	}

	@Test
	void aTokenIsRequired() throws Exception {
		save(null, body(true, true, false)).andExpect(status().isUnauthorized());
		mvc.perform(get("/v1/me/contact-methods")).andExpect(status().isUnauthorized());
	}

	@Test
	void theCardHasOnlyWhatTheUserChoseAndTheVerifiedMobile() throws Exception {
		TestAccount account = accounts.create();
		save(account, body(false, true, false)).andExpect(status().isOk());

		ContactCard card = contactMethods.contactCard(account.id()).orElseThrow();

		assertThat(contactMethods.hasContactMethod(account.id())).isTrue();
		assertThat(card.call()).isTrue();
		assertThat(card.whatsapp()).isFalse();
		assertThat(card.email()).isFalse();
		assertThat(card.mobile()).isEqualTo(accounts.mobileOf(account.id()));
		assertThat(card.emailAddress()).isNull();
	}

	@Test
	void theCardNeverPrintsTheNumber() throws Exception {
		TestAccount account = accounts.create();
		save(account, body(true, true, false)).andExpect(status().isOk());

		ContactCard card = contactMethods.contactCard(account.id()).orElseThrow();

		assertThat(card.toString()).doesNotContain(card.mobile()).doesNotContain(card.mobile().substring(3));
	}

}
