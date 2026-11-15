package com.gigwork.core.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.SqlCounter;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;
import com.jayway.jsonpath.JsonPath;

/** Where the shortlist stands in the lists: the Poster's applicant list, my applications and my Shortlisted tab. */
@IdentityIntegrationTest
class ShortlistListsApiTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	ReferenceData reference;
	@Autowired
	TestAccounts accounts;
	@Autowired
	MutableClock clock;
	@Autowired
	JdbcClient jdbc;

	HiringApiSupport api;
	TestAccount poster;
	TestAccount talent;
	UUID gig;
	UUID application;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		poster = accounts.create();
		accounts.setFirstName(poster.id(), "Priya");
		talent = api.talent("Asha", "photographer");
		gig = api.openGig(poster);
		application = api.applyOk(talent, gig);
	}

	private static String body(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

	private void tick() {
		clock.advance(Duration.ofSeconds(1));
	}

	// ---- the applicant list ---------------------------------------------------------------------------------------------

	@Test
	void theApplicantListShowsWhereTheShortlistStandsAndNeverTheContact() throws Exception {
		api.applicants(poster, gig, "").andExpect(jsonPath("$.items[0].shortlisted").value(false))
				.andExpect(jsonPath("$.items[0].posterShares").value(false)).andExpect(jsonPath("$.items[0].talentConsented").value(false));

		api.sharesContact(poster);
		api.sharesContact(talent);
		api.shortlist(poster, application, true).andExpect(status().isOk());
		api.applicants(poster, gig, "").andExpect(jsonPath("$.items[0].shortlisted").value(true))
				.andExpect(jsonPath("$.items[0].posterShares").value(true)).andExpect(jsonPath("$.items[0].talentConsented").value(false));

		api.consent(talent, application, true).andExpect(status().isOk());
		api.applicants(poster, gig, "").andExpect(jsonPath("$.items[0].talentConsented").value(true));

		api.consent(talent, application, false).andExpect(status().isOk());
		api.applicants(poster, gig, "").andExpect(jsonPath("$.items[0].talentConsented").value(false));

		api.consent(talent, application, true).andExpect(status().isOk());
		api.removeFromShortlist(poster, application).andExpect(status().isNoContent());
		api.applicants(poster, gig, "").andExpect(jsonPath("$.items[0].shortlisted").value(false))
				.andExpect(jsonPath("$.items[0].posterShares").value(false)).andExpect(jsonPath("$.items[0].talentConsented").value(false));
	}

	// ---- my applications -------------------------------------------------------------------------------------------------

	@Test
	void myApplicationsShowWhetherThePosterCurrentlyHasMeShortlisted() throws Exception {
		api.mine(talent, "").andExpect(jsonPath("$.items[0].shortlisted").value(false));
		api.shortlist(poster, application, false).andExpect(status().isOk());
		api.mine(talent, "").andExpect(jsonPath("$.items[0].shortlisted").value(true));
		api.removeFromShortlist(poster, application).andExpect(status().isNoContent());
		api.mine(talent, "").andExpect(jsonPath("$.items[0].shortlisted").value(false)); // FR-25a: not shortlisted any more
	}

	// ---- my Shortlisted tab -----------------------------------------------------------------------------------------------

	@Test
	void theShortlistedTabShowsTheGigThePostersNameAndTheConsentState() throws Exception {
		api.mine(talent, ""); // nothing yet
		api.shortlists(talent, "").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
		api.sharesContact(poster);
		api.shortlist(poster, application, true).andExpect(status().isOk());

		api.shortlists(talent, "").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].applicationId").value(application.toString()))
				.andExpect(jsonPath("$.items[0].posterFirstName").value("Priya")).andExpect(jsonPath("$.items[0].posterShares").value(true))
				.andExpect(jsonPath("$.items[0].consented").value(false)).andExpect(jsonPath("$.items[0].gig.id").value(gig.toString()))
				.andExpect(jsonPath("$.items[0].gig.status").value("OPEN")).andExpect(jsonPath("$.items[0].gig.title").exists());

		api.sharesContact(talent);
		api.consent(talent, application, true).andExpect(status().isOk());
		api.shortlists(talent, "").andExpect(jsonPath("$.items[0].consented").value(true)).andExpect(jsonPath("$.items[0].consentedAt").exists());
	}

	@Test
	void theTabDropsRemovedAndWithdrawnButKeepsClosedGigsWithTheirStatus() throws Exception {
		UUID kept = api.applyOk(talent, api.openGig(poster));
		UUID removed = api.applyOk(talent, api.openGig(poster));
		UUID withdrawn = api.applyOk(talent, api.openGig(poster));
		UUID closedGig = api.openGig(poster);
		UUID closed = api.applyOk(talent, closedGig);
		for (UUID a : List.of(kept, removed, withdrawn, closed)) {
			api.shortlist(poster, a, false).andExpect(status().isOk());
			tick();
		}
		api.removeFromShortlist(poster, removed).andExpect(status().isNoContent());
		api.withdraw(talent, withdrawn).andExpect(status().isNoContent());
		jdbc.sql("UPDATE gigs.gig SET status = 'CANCELLED', closed_at = now() WHERE id = :id").param("id", closedGig).update();

		String json = body(api.shortlists(talent, "").andExpect(jsonPath("$.items", hasSize(2))));

		assertThat((List<String>) JsonPath.read(json, "$.items[*].applicationId")).containsExactly(closed.toString(), kept.toString());
		assertThat((List<String>) JsonPath.read(json, "$.items[*].gig.status")).containsExactly("CANCELLED", "OPEN");
	}

	@Test
	void theTabPagesStablyAndOnlyShowsTheCallersOwnEntries() throws Exception {
		List<String> expected = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			UUID a = api.applyOk(talent, api.openGig(poster));
			api.shortlist(poster, a, false).andExpect(status().isOk());
			expected.add(0, a.toString());
			tick();
		}
		TestAccount other = api.talent("Bela", "photographer");
		api.shortlists(other, "").andExpect(jsonPath("$.items", hasSize(0)));

		String page1 = body(api.shortlists(talent, "?size=2"));
		UUID extra = api.applyOk(talent, api.openGig(poster));
		api.shortlist(poster, extra, false).andExpect(status().isOk()); // newer than the cursor: must not shift the pages
		String page2 = body(api.shortlists(talent, "?size=2&cursor=" + JsonPath.read(page1, "$.nextCursor")));
		String page3 = body(api.shortlists(talent, "?size=2&cursor=" + JsonPath.read(page2, "$.nextCursor")));

		List<String> seen = new ArrayList<>((List<String>) JsonPath.read(page1, "$.items[*].applicationId"));
		seen.addAll(JsonPath.read(page2, "$.items[*].applicationId"));
		seen.addAll(JsonPath.read(page3, "$.items[*].applicationId"));
		assertThat(seen).isEqualTo(expected);
		api.shortlists(talent, "?size=0").andExpect(status().isBadRequest());
		api.shortlists(talent, "?cursor=%25").andExpect(status().isBadRequest());
		api.shortlists(null, "").andExpect(status().isUnauthorized());
	}

	@Test
	void theTabUsesAFixedNumberOfStatementsHoweverLongThePage() throws Exception {
		for (int i = 0; i < 12; i++) {
			UUID a = api.applyOk(talent, api.openGig(poster));
			api.shortlist(poster, a, false).andExpect(status().isOk());
			tick();
		}
		api.shortlists(talent, "?size=2");
		long two = SqlCounter.during(() -> call(() -> api.shortlists(talent, "?size=2")));
		long twelve = SqlCounter.during(() -> call(() -> api.shortlists(talent, "?size=12")));
		assertThat(two).isPositive();
		assertThat(twelve).isEqualTo(two);
	}

	// ---- contacts are in no list ------------------------------------------------------------------------------------------

	@Test
	void noListEverCarriesAContactDetail() throws Exception {
		api.sharesContact(poster);
		api.sharesContact(talent);
		api.shortlist(poster, application, true).andExpect(status().isOk());
		api.consent(talent, application, true).andExpect(status().isOk());
		api.talentContact(poster, application).andExpect(status().isOk());
		api.posterContact(talent, application).andExpect(status().isOk());
		List<String> secrets = List.of(accounts.mobileOf(poster.id()), accounts.mobileOf(poster.id()).substring(3),
				accounts.mobileOf(talent.id()), accounts.mobileOf(talent.id()).substring(3));

		for (String json : List.of(body(api.applicants(poster, gig, "")), body(api.mine(talent, "")), body(api.shortlists(talent, "")),
				body(api.mine(poster, "")))) {
			for (String secret : secrets) {
				assertThat(json).doesNotContain(secret);
			}
			assertThat(json).doesNotContain("mobile", "whatsapp", "\"call\"");
		}
	}

	private interface Call {

		void run() throws Exception;

	}

	private static void call(Call call) {
		try {
			call.run();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
