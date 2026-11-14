package com.gigwork.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/** NFR-34: what visitors, logged-in users and the owner see of a Talent's profile (FR-8, FR-9). */
@IdentityIntegrationTest
class TalentProfileVisibilityTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	ReferenceData reference;
	@Autowired
	TestAccounts accounts;
	@Autowired
	JdbcClient jdbc;

	ProfileApiSupport api;
	TestAccount talent;
	TestAccount other;

	@BeforeEach
	void setUp() throws Exception {
		api = new ProfileApiSupport(mvc, reference);
		talent = accounts.create();
		other = accounts.create();
		accounts.setFirstName(talent.id(), "Asha");
		UUID anchor = api.categoryId("anchor");
		api.put(talent, "work", api.work("GROUP", 3, "Weddings and stage shows", "Twelve years on stage.", List.of(api.categoryId("photographer"), anchor),
				List.of(api.specialisationId("photographer", "wedding")), List.of("kn", "en"))).andExpect(status().isOk());
		api.put(talent, "location", ProfileApiSupport.locationByLocality(reference.searchLocalities("indiranagar", 1).get(0).id(), 17, true)).andExpect(status().isOk());
		api.put(talent, "portfolio", ProfileApiSupport.links("instagram.com/asha", "asha.in", "youtu.be/abc")).andExpect(status().isOk());
	}

	private String body(String bearer) throws Exception {
		return api.view(talent.id(), bearer).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private void assertNothingPrivate(String json) {
		assertThat(json).doesNotContain("\"lat\"").doesNotContain("\"lng\"").doesNotContain("latitude").doesNotContain("longitude")
				.doesNotContain("serviceRadiusKm").doesNotContain("willingToTravel").doesNotContain("\"status\"").doesNotContain("\"hidden\"")
				.doesNotContain("mobile").doesNotContain("email").doesNotContain("12.97").doesNotContain("77.64");
	}

	// ---- visitor ----------------------------------------------------------------------------------------------------

	@Test
	void aVisitorSeesTheProfileWithoutNameAndWithOnlyTheLinkTypes() throws Exception {
		api.view(talent.id(), null).andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(talent.id().toString()))
				.andExpect(jsonPath("$.firstName").doesNotExist()).andExpect(jsonPath("$.profileType").value("GROUP"))
				.andExpect(jsonPath("$.memberCount").value(3)).andExpect(jsonPath("$.headline").value("Weddings and stage shows"))
				.andExpect(jsonPath("$.bio").value("Twelve years on stage."))
				.andExpect(jsonPath("$.categories[*].slug", contains("photographer", "anchor")))
				.andExpect(jsonPath("$.specialisations[*].slug", contains("wedding")))
				.andExpect(jsonPath("$.languages[*].code", contains("en", "kn")))
				.andExpect(jsonPath("$.location.label").value("Indiranagar")).andExpect(jsonPath("$.location.cityName").value("Bengaluru"))
				.andExpect(jsonPath("$.portfolio[*].type", contains("INSTAGRAM", "WEBSITE", "YOUTUBE")))
				.andExpect(jsonPath("$.portfolio[0].url").doesNotExist()).andExpect(jsonPath("$.portfolio[1].url").doesNotExist());
		assertNothingPrivate(body(null));
		assertThat(body(null)).doesNotContain("instagram.com").doesNotContain("asha.in").doesNotContain("Asha\"");
	}

	// ---- logged-in user -----------------------------------------------------------------------------------------------

	@Test
	void aLoggedInUserAlsoSeesTheFirstNameAndTheLinks() throws Exception {
		api.view(talent.id(), other.header()).andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Asha"))
				.andExpect(jsonPath("$.portfolio[*].url", contains("https://instagram.com/asha", "https://asha.in", "https://youtu.be/abc")))
				.andExpect(jsonPath("$.portfolio[*].type", contains("INSTAGRAM", "WEBSITE", "YOUTUBE")))
				.andExpect(jsonPath("$.location.label").value("Indiranagar")).andExpect(jsonPath("$.headline").isNotEmpty());
		assertNothingPrivate(body(other.header()));
	}

	@Test
	void theNameIsAbsentWhenNotSet() throws Exception {
		TestAccount unnamed = accounts.create();
		api.put(unnamed, "work", api.simpleWork(api.categoryId("photographer")));
		api.put(unnamed, "location", ProfileApiSupport.locationByPoint(12.975, 77.605, 5, false));

		api.view(unnamed.id(), other.header()).andExpect(status().isOk()).andExpect(jsonPath("$.firstName").doesNotExist());
	}

	// ---- owner ----------------------------------------------------------------------------------------------------------

	@Test
	void theOwnerSeesTheirProfileInThePublicViewToo() throws Exception {
		api.view(talent.id(), talent.header()).andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Asha"))
				.andExpect(jsonPath("$.portfolio[0].url").value("https://instagram.com/asha"));
	}

	@Test
	void ownerEndpointShowsTheRadiusAndTravelPreferenceThatOthersNeverSee() throws Exception {
		api.getOwn(talent).andExpect(jsonPath("$.location.serviceRadiusKm").value(17)).andExpect(jsonPath("$.location.willingToTravel").value(true))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	// ---- 404 for everyone but the owner -----------------------------------------------------------------------------

	@Test
	void aHiddenProfileIsNotFoundByAnyoneButTheOwner() throws Exception {
		api.put(talent, "visibility", "{\"hidden\":true}").andExpect(status().isOk());

		api.view(talent.id(), null).andExpect(status().isNotFound());
		api.view(talent.id(), other.header()).andExpect(status().isNotFound());
		api.view(talent.id(), talent.header()).andExpect(status().isOk());

		api.put(talent, "visibility", "{\"hidden\":false}");
		api.view(talent.id(), null).andExpect(status().isOk());
	}

	@Test
	void anIncompleteProfileIsNotFoundByAnyoneButTheOwner() throws Exception {
		TestAccount half = accounts.create();
		api.put(half, "work", api.simpleWork(api.categoryId("photographer"))).andExpect(status().isOk());

		api.view(half.id(), null).andExpect(status().isNotFound());
		api.view(half.id(), other.header()).andExpect(status().isNotFound());
		api.view(half.id(), half.header()).andExpect(status().isOk()).andExpect(jsonPath("$.location").doesNotExist())
				.andExpect(jsonPath("$.headline").value("Wedding storyteller"));
	}

	@Test
	void aMissingProfileIsNotFoundEvenForAnAccountThatExists() throws Exception {
		api.view(UUID.randomUUID(), null).andExpect(status().isNotFound());
		api.view(other.id(), other.header()).andExpect(status().isNotFound());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/talent/not-a-uuid")).andExpect(status().isBadRequest());
	}

	// ---- tokens ----------------------------------------------------------------------------------------------------------

	@Test
	void publicMeansNoTokenRequiredNotAnyTokenAccepted() throws Exception {
		api.view(talent.id(), "Bearer not-a-valid-token").andExpect(status().isUnauthorized());
		api.view(talent.id(), "Bearer " + "x".repeat(40) + "." + "y".repeat(40) + "." + "z".repeat(40)).andExpect(status().isUnauthorized());
	}

	@Test
	void aHiddenProfileIsStillA401ForABadTokenNotA404() throws Exception {
		api.put(talent, "visibility", "{\"hidden\":true}");

		api.view(talent.id(), "Bearer garbage").andExpect(status().isUnauthorized());
	}

}
