package com.gigwork.core.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gigwork.core.identity.AccountNames;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;

@IdentityIntegrationTest
class NameEndpointTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	TestAccounts accounts;
	@Autowired
	AccountNames names;

	private ResultActions setName(TestAccount account, String firstName) throws Exception {
		String body = "{\"firstName\":" + (firstName == null ? "null" : "\"" + firstName.replace("\"", "\\\"") + "\"") + "}";
		return mvc.perform(put("/v1/me/name").header("Authorization", account.header()).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Asha", "D'Souza", "Mary-Ann", "A. R.", "ಆಶಾ", "आशा", "José", "Mária", "O’Neil" })
	void acceptsNamesMadeOfLettersSpacesApostrophesHyphensAndDots(String name) throws Exception {
		TestAccount account = accounts.create();

		setName(account, name).andExpect(status().isOk()).andExpect(jsonPath("$.firstName").isNotEmpty());

		assertThat(names.firstName(account.id())).isPresent();
	}

	@Test
	void trimsAndCollapsesSpaces() throws Exception {
		TestAccount account = accounts.create();

		setName(account, "  Asha   Rani ").andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Asha Rani"));

		assertThat(names.firstName(account.id())).contains("Asha Rani");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "Asha1", "4sha", "Asha_", "Asha!", "-Asha", ".Asha", "Asha@x.com", "A\tB", "Asha😀",
			"Asha\nRani" })
	void rejectsEmptyAndCharactersThatAreNotPartOfAName(String name) throws Exception {
		TestAccount account = accounts.create();

		setName(account, name).andExpect(status().isBadRequest());

		assertThat(names.firstName(account.id())).isEmpty();
	}

	@Test
	void rejectsMoreThan50CharactersButAcceptsExactly50() throws Exception {
		TestAccount account = accounts.create();

		setName(account, "A".repeat(51)).andExpect(status().isBadRequest());
		setName(account, "A".repeat(50)).andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = { "wa.me", "t.me", "telegram.me", "api.whatsapp.com" })
	void rejectsContactLinksWithTheContactMessage(String name) throws Exception {
		TestAccount account = accounts.create();

		setName(account, name).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Contact details can't be in your profile. Posters reach you through GigWork."));
	}

	@Test
	void aMissingNameIsABadRequest() throws Exception {
		setName(accounts.create(), null).andExpect(status().isBadRequest());
	}

	@Test
	void needsAToken() throws Exception {
		mvc.perform(put("/v1/me/name").contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Asha\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anAccountThatDoesNotExistIsNotFound() throws Exception {
		TestAccount ghost = new TestAccount(UUID.randomUUID(), null);
		String token = accounts.token(ghost.id());

		mvc.perform(put("/v1/me/name").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"Asha\"}")).andExpect(status().isNotFound());
	}

	@Test
	void nameIsAbsentUntilSetAndReadableThroughTheApiAfter() throws Exception {
		TestAccount account = accounts.create();
		assertThat(names.firstName(account.id())).isEmpty();
		assertThat(names.firstName(UUID.randomUUID())).isEmpty();

		setName(account, "Asha").andExpect(status().isOk());
		setName(account, "Rani").andExpect(status().isOk());

		assertThat(names.firstName(account.id())).contains("Rani");
	}

	@Test
	void aNameCanOnlyBeSetForTheCallersOwnAccount() throws Exception {
		TestAccount mine = accounts.create();
		TestAccount other = accounts.create();

		setName(mine, "Asha").andExpect(status().isOk());

		assertThat(names.firstName(other.id())).isEmpty();
	}

}
