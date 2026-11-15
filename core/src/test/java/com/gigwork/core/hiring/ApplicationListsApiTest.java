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

/** The two lists of applications (FR-22, FR-24): what each viewer sees, in which order, and how it is fetched. */
@IdentityIntegrationTest
class ApplicationListsApiTest {

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

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		poster = accounts.create();
		talent = api.talent("Asha", "photographer");
	}

	private static String body(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

	private static List<String> ids(String json) {
		return JsonPath.read(json, "$.items[*].id");
	}

	private void tick() {
		clock.advance(Duration.ofSeconds(1));
	}

	// ---- my applications -------------------------------------------------------------------------------------------------

	@Test
	void myApplicationsShowActiveWithdrawnAndClosedGigsWithTheGigsStatusNewestFirst() throws Exception {
		UUID open = api.openGig(poster);
		UUID cancelled = api.openGig(poster);
		UUID filled = api.openGig(poster);
		UUID expired = api.openGig(poster);
		UUID withdrawnGig = api.openGig(poster);
		List<UUID> applications = new ArrayList<>();
		for (UUID gig : List.of(open, cancelled, filled, expired, withdrawnGig)) {
			applications.add(api.applyOk(talent, gig));
			tick();
		}
		api.withdraw(talent, applications.get(4)).andExpect(status().isNoContent());
		jdbc.sql("UPDATE gigs.gig SET status = 'CANCELLED', closed_at = now() WHERE id = :id").param("id", cancelled).update();
		jdbc.sql("UPDATE gigs.gig SET status = 'FILLED' WHERE id = :id").param("id", filled).update();
		jdbc.sql("UPDATE gigs.gig SET status = 'EXPIRED', closed_at = now() WHERE id = :id").param("id", expired).update();

		String json = body(api.mine(talent, "").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(5))));

		assertThat(ids(json)).containsExactly(applications.get(4).toString(), applications.get(3).toString(),
				applications.get(2).toString(), applications.get(1).toString(), applications.get(0).toString());
		assertThat((List<String>) JsonPath.read(json, "$.items[*].status")).containsExactly("WITHDRAWN", "ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE");
		assertThat((List<String>) JsonPath.read(json, "$.items[*].gig.status")).containsExactly("OPEN", "EXPIRED", "FILLED", "CANCELLED", "OPEN");
		assertThat((String) JsonPath.read(json, "$.items[4].gig.title")).isEqualTo("Photographer for a two-day wedding");
		assertThat((String) JsonPath.read(json, "$.items[4].gig.categoryName")).isEqualTo("Photographer");
		assertThat(json).doesNotContain("posterId", "lat", "lng", "busyWarning");
	}

	@Test
	void applyingAgainMovesAnApplicationToTheTop() throws Exception {
		UUID first = api.applyOk(talent, api.openGig(poster));
		tick();
		UUID second = api.applyOk(talent, api.openGig(poster));
		tick();
		api.withdraw(talent, first).andExpect(status().isNoContent());
		api.apply(talent, jdbc.sql("SELECT gig_id FROM hiring.application WHERE id = :id").param("id", first).query(UUID.class).single())
				.andExpect(status().isCreated());

		assertThat(ids(body(api.mine(talent, "")))).containsExactly(first.toString(), second.toString());
	}

	@Test
	void myApplicationsPageStablyEvenWhenNewApplicationsArriveBetweenPages() throws Exception {
		List<String> expected = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			expected.add(0, api.applyOk(talent, api.openGig(poster)).toString());
			tick();
		}

		String page1 = body(api.mine(talent, "?size=2").andExpect(jsonPath("$.items", hasSize(2))));
		String cursor = JsonPath.read(page1, "$.nextCursor");
		api.applyOk(talent, api.openGig(poster)); // newer than the cursor: must not shift the next page
		tick();
		String page2 = body(api.mine(talent, "?size=2&cursor=" + cursor).andExpect(jsonPath("$.items", hasSize(2))));
		String page3 = body(api.mine(talent, "?size=2&cursor=" + JsonPath.read(page2, "$.nextCursor")));

		List<String> seen = new ArrayList<>(ids(page1));
		seen.addAll(ids(page2));
		seen.addAll(ids(page3));
		assertThat(seen).isEqualTo(expected);
		assertThat(JsonPath.<Object>read(page3, "$.items")).asList().hasSize(1);
		assertThat(page3).doesNotContain("nextCursor");
	}

	@Test
	void aTalentSeesOnlyTheirOwnApplications() throws Exception {
		api.applyOk(talent, api.openGig(poster));
		TestAccount other = api.talent("Bela", "photographer");

		api.mine(other, "").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void badPagingAndMissingTokensAreRefused() throws Exception {
		api.mine(talent, "?size=0").andExpect(status().isBadRequest());
		api.mine(talent, "?size=51").andExpect(status().isBadRequest());
		api.mine(talent, "?cursor=not-a-cursor").andExpect(status().isBadRequest());
		api.mine(null, "").andExpect(status().isUnauthorized());
	}

	@Test
	void myApplicationsUseAFixedNumberOfStatementsHoweverLongThePage() throws Exception {
		for (int i = 0; i < 12; i++) {
			api.applyOk(talent, api.openGig(poster));
			tick();
		}
		api.mine(talent, "?size=2"); // warm caches, security and the first request's one-off work
		long two = SqlCounter.during(() -> call(() -> api.mine(talent, "?size=2")));
		long twelve = SqlCounter.during(() -> call(() -> api.mine(talent, "?size=12")));
		assertThat(two).isPositive();
		assertThat(twelve).isEqualTo(two);
	}

	// ---- a gig's applicants -------------------------------------------------------------------------------------------------

	@Test
	void thePosterSeesActiveApplicationsOldestFirstWithTheApplicantsProfile() throws Exception {
		UUID gig = api.openGig(poster);
		TestAccount bela = api.talent("Bela", "photographer");
		TestAccount chitra = api.talent("Chitra", "photographer");
		UUID a = api.applyOk(talent, gig);
		tick();
		UUID b = HiringApiSupport.id(api.apply(bela, gig, "{\"note\":\"Available all day\"}").andExpect(status().isCreated()));
		tick();
		UUID c = api.applyOk(chitra, gig);
		api.withdraw(chitra, c).andExpect(status().isNoContent());

		String json = body(api.applicants(poster, gig, "").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(2))));

		assertThat(ids(json)).containsExactly(a.toString(), b.toString());
		assertThat((String) JsonPath.read(json, "$.items[1].note")).isEqualTo("Available all day");
		assertThat((String) JsonPath.read(json, "$.items[0].profile.firstName")).isEqualTo("Asha");
		assertThat((String) JsonPath.read(json, "$.items[0].profile.headline")).isEqualTo("Wedding storyteller");
		assertThat((List<String>) JsonPath.read(json, "$.items[0].profile.categories[*].slug")).containsExactly("photographer");
		assertThat((String) JsonPath.read(json, "$.items[0].profile.portfolio[0].url")).contains("instagram.com/asha");
		assertThat((String) JsonPath.read(json, "$.items[0].appliedAt")).isNotBlank();
	}

	@Test
	void theApplicantListNeverShowsContactDetailsTheBusyWarningOrPrivateProfileFields() throws Exception {
		UUID gig = api.openGig(poster);
		var start = clock.instant().plus(Duration.ofDays(3)).plus(Duration.ofHours(1));
		api.busyBlock(talent, start, start.plus(Duration.ofHours(1))).andExpect(status().isCreated());
		api.applyAcknowledgingBusy(talent, gig).andExpect(status().isCreated()).andExpect(jsonPath("$.busyWarningShown").value(true));

		String json = body(api.applicants(poster, gig, "").andExpect(status().isOk()));

		assertThat(json).doesNotContain("busyWarning", "busy_warning", "mobile", "email", "phone", "lat", "lng", "serviceRadius",
				"willingToTravel", "\"status\"", "hidden", "periods");
		assertThat(json).doesNotContainPattern("[6-9][0-9]{9}");
	}

	@Test
	void aTalentWhoPausedTheirProfileStillShowsWhileTheirApplicationIsActive() throws Exception {
		UUID gig = api.openGig(poster);
		api.applyOk(talent, gig);
		api.profiles.put(talent, "visibility", "{\"hidden\":true}").andExpect(status().isOk());

		api.applicants(poster, gig, "").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].profile.firstName").value("Asha"))
				.andExpect(jsonPath("$.items[0].profile.headline").value("Wedding storyteller"));
		// ...but a paused profile is still not discoverable
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/talent/" + talent.id())
				.header("Authorization", poster.header())).andExpect(status().isNotFound());

		api.withdraw(talent, jdbc.sql("SELECT id FROM hiring.application WHERE talent_id = :t").param("t", talent.id()).query(UUID.class).single())
				.andExpect(status().isNoContent());
		api.applicants(poster, gig, "").andExpect(jsonPath("$.items", hasSize(0))); // withdrawing ends it
	}

	@Test
	void onlyTheGigsPosterCanListItsApplicants() throws Exception {
		UUID gig = api.openGig(poster);
		api.applyOk(talent, gig);
		TestAccount otherPoster = accounts.create();

		api.applicants(otherPoster, gig, "").andExpect(status().isNotFound());
		api.applicants(talent, gig, "").andExpect(status().isNotFound()); // not even an applicant
		api.applicants(poster, UUID.randomUUID(), "").andExpect(status().isNotFound());
		api.applicants(null, gig, "").andExpect(status().isUnauthorized());
		api.applicants(poster, gig, "").andExpect(status().isOk());
	}

	@Test
	void anApplicantListOfAClosedGigStillWorksForItsPoster() throws Exception {
		UUID gig = api.openGig(poster);
		api.applyOk(talent, gig);
		jdbc.sql("UPDATE gigs.gig SET status = 'CANCELLED', closed_at = now() WHERE id = :id").param("id", gig).update();

		api.applicants(poster, gig, "").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)));
	}

	@Test
	void applicantsPageStablyOldestFirstAndRefuseBadPaging() throws Exception {
		UUID gig = api.openGig(poster);
		List<String> expected = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			expected.add(api.applyOk(api.talent("T" + i, "photographer"), gig).toString());
			tick();
		}

		String page1 = body(api.applicants(poster, gig, "?size=2"));
		String page2 = body(api.applicants(poster, gig, "?size=2&cursor=" + JsonPath.read(page1, "$.nextCursor")));
		api.applyOk(api.talent("Late", "photographer"), gig); // newest: lands after the pages being read
		String page3 = body(api.applicants(poster, gig, "?size=2&cursor=" + JsonPath.read(page2, "$.nextCursor")));

		List<String> seen = new ArrayList<>(ids(page1));
		seen.addAll(ids(page2));
		seen.addAll(ids(page3));
		assertThat(seen.subList(0, 5)).isEqualTo(expected);
		api.applicants(poster, gig, "?size=0").andExpect(status().isBadRequest());
		api.applicants(poster, gig, "?cursor=%25%25").andExpect(status().isBadRequest());
	}

	@Test
	void applicantsUseAFixedNumberOfStatementsHoweverLongThePage() throws Exception {
		UUID gig = api.openGig(poster);
		for (int i = 0; i < 12; i++) {
			api.applyOk(api.talent("T" + i, "photographer"), gig);
			tick();
		}
		api.applicants(poster, gig, "?size=2");
		long two = SqlCounter.during(() -> call(() -> api.applicants(poster, gig, "?size=2")));
		long twelve = SqlCounter.during(() -> call(() -> api.applicants(poster, gig, "?size=12")));
		assertThat(two).isPositive();
		assertThat(twelve).isEqualTo(two);
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
