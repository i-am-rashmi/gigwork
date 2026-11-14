package com.gigwork.core.reference.web;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.reference.ReferenceData;
import com.gigwork.core.reference.support.ReferenceIntegrationTest;
import com.gigwork.core.reference.support.SeedFixtures;

/** The public endpoints, called with no token at all. */
@ReferenceIntegrationTest
class ReferenceEndpointsTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	ReferenceData reference;

	@Test
	void categoriesAreOpenToVisitorsWithSpecialisationsAndTheLanguageFlag() throws Exception {
		mvc.perform(get("/v1/categories")).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(SeedFixtures.rows("categories.csv"))))
				.andExpect(jsonPath("$[0].slug").value("photographer"))
				.andExpect(jsonPath("$[0].workMode").value("ON_SITE"))
				.andExpect(jsonPath("$[0].languageRelevant").value(false))
				.andExpect(jsonPath("$[0].specialisations[0].slug").value("wedding"))
				.andExpect(jsonPath("$[0].specialisations[0].id").isNotEmpty())
				.andExpect(jsonPath("$[?(@.slug == 'anchor')].languageRelevant").value(true));
	}

	@Test
	void languagesCitiesAndZonesAreOpenToVisitors() throws Exception {
		mvc.perform(get("/v1/languages")).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(SeedFixtures.rows("languages.csv"))))
				.andExpect(jsonPath("$[0].code").value("en")).andExpect(jsonPath("$[0].name").value("English"));

		mvc.perform(get("/v1/cities")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].slug").value("bengaluru"));

		UUID city = reference.liveCities().get(0).id();
		mvc.perform(get("/v1/cities/" + city + "/zones")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(5)))
				.andExpect(jsonPath("$[0].name").value("Central")).andExpect(jsonPath("$[0].id").isNotEmpty())
				.andExpect(jsonPath("$[0].boundary").doesNotExist());
	}

	@Test
	void zonesOfAnUnknownCityAreNotFound() throws Exception {
		mvc.perform(get("/v1/cities/" + UUID.randomUUID() + "/zones")).andExpect(status().isNotFound());
		mvc.perform(get("/v1/cities/not-a-uuid/zones")).andExpect(status().isBadRequest());
	}

	@Test
	void localitySearchReturnsNamePinPointAndZone() throws Exception {
		mvc.perform(get("/v1/localities").param("q", "indiranagar")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Indiranagar")).andExpect(jsonPath("$[0].pin").isNotEmpty())
				.andExpect(jsonPath("$[0].lat").isNumber()).andExpect(jsonPath("$[0].lng").isNumber())
				.andExpect(jsonPath("$[0].zoneId").isNotEmpty());
	}

	@Test
	void localitySearchByPinPrefixAndTheDefaultAndExplicitLimit() throws Exception {
		mvc.perform(get("/v1/localities").param("q", "5601")).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(10))).andExpect(jsonPath("$[*].pin", everyItem(startsWith("5601"))));
		mvc.perform(get("/v1/localities").param("q", "5601").param("limit", "3")).andExpect(jsonPath("$", hasSize(3)));
		mvc.perform(get("/v1/localities").param("q", "5601").param("limit", "50")).andExpect(jsonPath("$", hasSize(50)));
		mvc.perform(get("/v1/localities").param("q", "5")).andExpect(status().isOk()); // one digit is enough for a PIN
	}

	@Test
	void duplicateNamesComeBackAsSeparateEntries() throws Exception {
		mvc.perform(get("/v1/localities").param("q", "Maruthi Nagara")).andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Maruthi Nagara')]", hasSize(4)));
	}

	@Test
	void badLocalityQueriesAreRejectedWith400() throws Exception {
		mvc.perform(get("/v1/localities")).andExpect(status().isBadRequest()); // q missing
		mvc.perform(get("/v1/localities").param("q", "")).andExpect(status().isBadRequest());
		mvc.perform(get("/v1/localities").param("q", "a")).andExpect(status().isBadRequest());
		mvc.perform(get("/v1/localities").param("q", " a ")).andExpect(status().isBadRequest());
		mvc.perform(get("/v1/localities").param("q", "ab").param("limit", "0")).andExpect(status().isBadRequest());
		mvc.perform(get("/v1/localities").param("q", "ab").param("limit", "51")).andExpect(status().isBadRequest());
		mvc.perform(get("/v1/localities").param("q", "ab").param("limit", "many")).andExpect(status().isBadRequest());
		mvc.perform(get("/v1/localities").param("q", "ab")).andExpect(status().isOk());
	}

	@Test
	void publicMeansNoTokenRequiredNotAnyTokenAccepted() throws Exception {
		mvc.perform(get("/v1/categories").header("Authorization", "Bearer not-a-valid-token"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void onlyGetIsAllowed() throws Exception {
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/categories"))
				.andExpect(status().is4xxClientError());
	}

}
