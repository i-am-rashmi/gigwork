package com.gigwork.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.SqlCounter;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/** The module's read-only API for other modules. */
@IdentityIntegrationTest
class TalentProfilesTest {

	@Autowired
	TalentProfiles profiles;
	@Autowired
	MockMvc mvc;
	@Autowired
	ReferenceData reference;
	@Autowired
	TestAccounts accounts;

	@Test
	void noProfileUntilSomethingIsSavedThenAnyStatusCounts() throws Exception {
		ProfileApiSupport api = new ProfileApiSupport(mvc, reference);
		TestAccount talent = accounts.create();
		assertThat(profiles.summary(talent.id())).isEmpty();
		assertThat(profiles.summary(UUID.randomUUID())).isEmpty();

		api.put(talent, "work", api.simpleWork(api.categoryId("photographer"))).andExpect(status().isOk());
		assertThat(profiles.summary(talent.id())).hasValueSatisfying(s -> {
			assertThat(s.status()).isEqualTo("INCOMPLETE");
			assertThat(s.cityId()).isNull(); // no location yet
		});

		api.put(talent, "location", ProfileApiSupport.locationByLocality(reference.searchLocalities("indiranagar", 1).get(0).id(), 10, true));
		api.put(talent, "visibility", "{\"hidden\":true}");
		assertThat(profiles.summary(talent.id())).hasValueSatisfying(s -> {
			assertThat(s.status()).isEqualTo("HIDDEN");
			assertThat(s.cityId()).isEqualTo(reference.liveCities().get(0).id());
		});
	}

	private TestAccount talent(ProfileApiSupport api, String firstName, String category, boolean complete, boolean hidden)
			throws Exception {
		TestAccount t = accounts.create();
		accounts.setFirstName(t.id(), firstName);
		api.put(t, "work", api.simpleWork(api.categoryId(category))).andExpect(status().isOk());
		if (complete) {
			api.put(t, "location", ProfileApiSupport.locationByLocality(reference.searchLocalities("indiranagar", 1).get(0).id(), 10, true))
					.andExpect(status().isOk());
			api.put(t, "portfolio", ProfileApiSupport.links("instagram.com/" + firstName.toLowerCase())).andExpect(status().isOk());
		}
		if (hidden) {
			api.put(t, "visibility", "{\"hidden\":true}").andExpect(status().isOk());
		}
		return t;
	}

	@Test
	void eligibilityIsTheStatusAndTheCategoriesOnly() throws Exception {
		ProfileApiSupport api = new ProfileApiSupport(mvc, reference);
		assertThat(profiles.eligibility(UUID.randomUUID())).isEmpty();
		TestAccount incomplete = talent(api, "Ira", "photographer", false, false);
		TestAccount active = accounts.create();
		accounts.setFirstName(active.id(), "Asha");
		api.put(active, "work", api.simpleWork(api.categoryId("photographer"), api.categoryId("anchor"))).andExpect(status().isOk());
		api.put(active, "location", ProfileApiSupport.locationByLocality(reference.searchLocalities("indiranagar", 1).get(0).id(), 10, true));

		assertThat(profiles.eligibility(incomplete.id())).hasValueSatisfying(e -> {
			assertThat(e.isActive()).isFalse();
			assertThat(e.status()).isEqualTo("INCOMPLETE");
			assertThat(e.categoryIds()).containsExactly(api.categoryId("photographer"));
		});
		assertThat(profiles.eligibility(active.id())).hasValueSatisfying(e -> {
			assertThat(e.isActive()).isTrue();
			assertThat(e.categoryIds()).containsExactlyInAnyOrder(api.categoryId("photographer"), api.categoryId("anchor"));
		});
	}

	@Test
	void applicantProfilesCoverActiveAndHiddenButNeverIncompleteOrUnknown() throws Exception {
		ProfileApiSupport api = new ProfileApiSupport(mvc, reference);
		TestAccount active = talent(api, "Asha", "photographer", true, false);
		TestAccount hidden = talent(api, "Bela", "photographer", true, true);
		TestAccount incomplete = talent(api, "Ira", "photographer", false, false);

		var found = profiles.applicantProfiles(List.of(active.id(), hidden.id(), incomplete.id(), UUID.randomUUID(), active.id()));

		assertThat(found).containsOnlyKeys(active.id(), hidden.id());
		ApplicantProfile a = found.get(active.id());
		assertThat(a.firstName()).isEqualTo("Asha");
		assertThat(a.headline()).isEqualTo("Wedding storyteller");
		assertThat(a.categories()).extracting(ApplicantProfile.Named::slug).containsExactly("photographer");
		assertThat(a.location()).isNotNull();
		assertThat(a.portfolio()).extracting(ApplicantProfile.Link::url).allSatisfy(u -> assertThat(u).contains("instagram.com/asha"));
		assertThat(found.get(hidden.id()).firstName()).isEqualTo("Bela");
		assertThat(profiles.applicantProfiles(List.of())).isEmpty();
	}

	@Test
	void applicantProfilesNeverShowPrivateFields() throws Exception {
		ProfileApiSupport api = new ProfileApiSupport(mvc, reference);
		TestAccount t = talent(api, "Asha", "photographer", true, false);
		String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(profiles.applicantProfiles(List.of(t.id())).get(t.id()));
		assertThat(json).doesNotContain("lat", "lng", "radius", "willing", "status", "hidden", "mobile", "email");
	}

	@Test
	void applicantProfilesUseAFixedNumberOfStatementsHoweverManyAccounts() throws Exception {
		ProfileApiSupport api = new ProfileApiSupport(mvc, reference);
		List<UUID> ids = new java.util.ArrayList<>();
		for (int i = 0; i < 12; i++) {
			ids.add(talent(api, "Name" + i, "photographer", true, i % 3 == 0).id());
		}
		profiles.applicantProfiles(ids.subList(0, 2)); // warm the reference caches
		long two = SqlCounter.during(() -> profiles.applicantProfiles(ids.subList(0, 2)));
		long twelve = SqlCounter.during(() -> assertThat(profiles.applicantProfiles(ids)).hasSize(12));
		assertThat(two).isPositive();
		assertThat(twelve).isEqualTo(two);
	}

}
