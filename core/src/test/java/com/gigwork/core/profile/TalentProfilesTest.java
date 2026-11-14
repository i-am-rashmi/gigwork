package com.gigwork.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.identity.support.IdentityIntegrationTest;
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

}
