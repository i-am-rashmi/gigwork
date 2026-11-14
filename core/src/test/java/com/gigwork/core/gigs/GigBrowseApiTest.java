package com.gigwork.core.gigs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.LocalityInfo;
import com.gigwork.core.reference.ReferenceData;
import com.jayway.jsonpath.JsonPath;

/** Browsing open gigs (FR-9, FR-11 in Phase 1): who sees what, the filters, and paging that never repeats or skips. */
@IdentityIntegrationTest
class GigBrowseApiTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	ReferenceData reference;
	@Autowired
	MutableClock clock;
	@Autowired
	TestAccounts accounts;
	@Autowired
	JdbcClient jdbc;

	GigApiSupport api;
	TestAccount poster;
	TestAccount viewer;

	@BeforeEach
	void setUp() {
		clock.reset();
		jdbc.sql("DELETE FROM gigs.gig").update();
		api = new GigApiSupport(mvc, reference, clock);
		poster = accounts.create();
		accounts.setFirstName(poster.id(), "Meera");
		viewer = accounts.create();
	}

	@AfterEach
	void tearDown() {
		clock.reset();
		jdbc.sql("DELETE FROM gigs.gig").update();
	}

	/** Publishes, then moves the clock a second so the next gig is strictly newer. */
	private UUID publish(GigApiSupport.Gig gig) throws Exception {
		UUID id = api.publishOk(poster, gig);
		clock.advance(Duration.ofSeconds(1));
		return id;
	}

	private ResultActions list(TestAccount who, String query) throws Exception {
		return api.get(who, "/gigs" + (query.isEmpty() ? "" : "?" + query));
	}

	private List<String> ids(ResultActions result) throws Exception {
		return JsonPath.read(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.items[*].id");
	}

	/** The next cursor of a page, or null on the last page (where the field is left out). */
	private static String nextCursor(String body) {
		return JsonPath.using(com.jayway.jsonpath.Configuration.defaultConfiguration()
				.addOptions(com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL)).parse(body).read("$.nextCursor", String.class);
	}

	private List<String> strings(UUID... ids) {
		return java.util.Arrays.stream(ids).map(UUID::toString).toList();
	}

	/** Two localities in different zones of the seeded city. */
	private LocalityInfo[] twoZones() {
		LocalityInfo first = reference.searchLocalities("indiranagar", 1).get(0);
		LocalityInfo second = java.util.stream.Stream.of("a", "e", "o", "i", "u").flatMap(t -> reference.searchLocalities(t, 200).stream()).filter(l -> !l.zoneId().equals(first.zoneId())).findFirst().orElseThrow();
		return new LocalityInfo[] { first, second };
	}

	// ---- who sees what -------------------------------------------------------------------------------------------

	@Test
	void aVisitorSeesTheGigWithoutThePostersNameAndWithoutAnyExactLocation() throws Exception {
		UUID id = publish(api.onSite().budget("1000", "2000"));

		list(null, "").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].id").value(id.toString())).andExpect(jsonPath("$.items[0].title").isNotEmpty())
				.andExpect(jsonPath("$.items[0].locationLabel").isNotEmpty()).andExpect(jsonPath("$.items[0].cityName").value("Bengaluru"))
				.andExpect(jsonPath("$.items[0].categoryName").value("Photographer")).andExpect(jsonPath("$.items[0].budgetMin").value(1000))
				.andExpect(jsonPath("$.items[0].taOffered").value(true)).andExpect(jsonPath("$.items[0].workMode").value("ON_SITE"))
				.andExpect(jsonPath("$.items[0].posterFirstName").doesNotExist())
				.andExpect(jsonPath("$.items[0].updatedAt").doesNotExist()).andExpect(jsonPath("$.items[0].closedAt").doesNotExist());
		api.get(null, "/gigs/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.posterFirstName").doesNotExist());
	}

	@Test
	void aLoggedInUserAlsoSeesThePostersFirstNameInTheListAndOnTheGig() throws Exception {
		UUID id = publish(api.onSite());

		list(viewer, "").andExpect(jsonPath("$.items[0].posterFirstName").value("Meera"))
				.andExpect(jsonPath("$.items[0].closeReason").doesNotExist()).andExpect(jsonPath("$.items[0].updatedAt").doesNotExist());
		api.get(viewer, "/gigs/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.posterFirstName").value("Meera"));
	}

	@Test
	void noResponseEverContainsCoordinatesOrAnApplicantCount() throws Exception {
		UUID id = publish(api.onSite().point(12.97649, 77.60451));
		publish(api.remote());
		String[] forbidden = { "\"lat\"", "\"lng\"", "latitude", "longitude", "\"point\"", "coordinates", "applicant", "\"location\"" };

		for (TestAccount who : new TestAccount[] { null, viewer, poster }) {
			for (ResultActions result : List.of(list(who, ""), api.get(who, "/gigs/" + id), api.get(poster, "/me/gigs"))) {
				String body = result.andReturn().getResponse().getContentAsString();
				for (String word : forbidden) {
					assertThat(body).as(word).doesNotContainIgnoringCase(word);
				}
			}
		}
	}

	@Test
	void aTokenThatIsNotValidIsRefusedEvenOnAPublicEndpoint() throws Exception {
		mvc.perform(get("/v1/gigs").header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized());
		mvc.perform(get("/v1/gigs/" + UUID.randomUUID()).header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized());
	}

	// ---- a single gig ----------------------------------------------------------------------------------------------

	@Test
	void aGigThatIsNotOpenIsNotFoundForEveryoneButItsPoster() throws Exception {
		UUID cancelled = publish(api.onSite());
		api.cancel(poster, cancelled, "{\"reason\":\"No longer needed\"}").andExpect(status().isOk());
		UUID filled = publish(api.onSite());
		jdbc.sql("UPDATE gigs.gig SET status = 'FILLED' WHERE id = :id").param("id", filled).update();
		UUID expired = publish(api.onSite());
		jdbc.sql("UPDATE gigs.gig SET status = 'EXPIRED', closed_at = now() WHERE id = :id").param("id", expired).update();

		for (UUID id : new UUID[] { cancelled, filled, expired }) {
			api.get(null, "/gigs/" + id).andExpect(status().isNotFound());
			api.get(viewer, "/gigs/" + id).andExpect(status().isNotFound());
			api.get(poster, "/gigs/" + id).andExpect(status().isOk());
		}
		api.get(poster, "/gigs/" + cancelled).andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.closeReason").value("No longer needed")).andExpect(jsonPath("$.closedAt").isNotEmpty());
		api.get(null, "/gigs/" + UUID.randomUUID()).andExpect(status().isNotFound());
	}

	@Test
	void thePosterSeesTheBookkeepingOfAnOpenGigButOthersDoNot() throws Exception {
		UUID id = publish(api.onSite());

		api.get(poster, "/gigs/" + id).andExpect(jsonPath("$.updatedAt").isNotEmpty()).andExpect(jsonPath("$.status").value("OPEN"));
		api.get(viewer, "/gigs/" + id).andExpect(jsonPath("$.updatedAt").doesNotExist());
	}

	@Test
	void anIdThatIsNotAUuidIsABadRequest() throws Exception {
		api.get(null, "/gigs/not-a-uuid").andExpect(status().isBadRequest());
	}

	// ---- the list ---------------------------------------------------------------------------------------------------

	@Test
	void onlyOpenGigsAreListedNewestFirst() throws Exception {
		UUID oldest = publish(api.onSite());
		UUID cancelled = publish(api.onSite());
		api.cancel(poster, cancelled, "").andExpect(status().isOk());
		UUID middle = publish(api.remote());
		UUID expired = publish(api.onSite());
		jdbc.sql("UPDATE gigs.gig SET status = 'EXPIRED', closed_at = now() WHERE id = :id").param("id", expired).update();
		UUID newest = publish(api.onSite());

		assertThat(ids(list(null, ""))).containsExactlyElementsOf(strings(newest, middle, oldest));
	}

	@Test
	void anEmptyListIsAnEmptyPageWithNoCursor() throws Exception {
		list(null, "").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0))).andExpect(jsonPath("$.nextCursor").doesNotExist());
	}

	// ---- filters ---------------------------------------------------------------------------------------------------

	@Test
	void theCategoryFilterTakesOneOrSeveralCategories() throws Exception {
		UUID photo = publish(api.onSite());
		UUID anchor = publish(api.onSite().category(api.category("anchor")));
		UUID video = publish(api.onSite().category(api.category("videographer")));

		assertThat(ids(list(null, "categoryId=" + api.category("anchor")))).containsExactly(anchor.toString());
		assertThat(ids(list(null, "categoryId=" + api.category("anchor") + "&categoryId=" + api.category("photographer"))))
				.containsExactlyElementsOf(strings(anchor, photo));
		assertThat(ids(list(null, "categoryId=" + UUID.randomUUID()))).isEmpty();
		assertThat(video).isNotNull();
	}

	@Test
	void theCityFilterKeepsOnSiteGigsOfThatCityAndEveryRemoteGig() throws Exception {
		UUID city = reference.liveCities().get(0).id();
		UUID onSite = publish(api.onSite());
		UUID remote = publish(api.remote());

		assertThat(ids(list(null, "cityId=" + city))).containsExactlyElementsOf(strings(remote, onSite));
		assertThat(ids(list(null, "cityId=" + UUID.randomUUID()))).containsExactly(remote.toString());
	}

	@Test
	void theZoneFilterKeepsGigsInThoseZonesAndEveryRemoteGig() throws Exception {
		LocalityInfo[] places = twoZones();
		UUID inFirst = publish(api.onSite().locality(places[0].id()));
		UUID inSecond = publish(api.onSite().locality(places[1].id()));
		UUID remote = publish(api.remote());

		assertThat(ids(list(null, "zoneId=" + places[0].zoneId()))).containsExactlyElementsOf(strings(remote, inFirst));
		assertThat(ids(list(null, "zoneId=" + places[1].zoneId()))).containsExactlyElementsOf(strings(remote, inSecond));
		assertThat(ids(list(null, "zoneId=" + places[0].zoneId() + "&zoneId=" + places[1].zoneId())))
				.containsExactlyElementsOf(strings(remote, inSecond, inFirst));
	}

	@Test
	void aRemoteGigIsPresentWithAnyZoneFilterEvenOneThatDoesNotExist() throws Exception {
		UUID remote = publish(api.remote());
		publish(api.onSite());

		assertThat(ids(list(null, "zoneId=" + UUID.randomUUID()))).containsExactly(remote.toString());
	}

	@Test
	void aCityAndAZoneOfAnotherCityMatchNoOnSiteGig() throws Exception {
		LocalityInfo[] places = twoZones();
		UUID remote = publish(api.remote());
		publish(api.onSite().locality(places[0].id()));

		assertThat(ids(list(null, "cityId=" + UUID.randomUUID() + "&zoneId=" + places[0].zoneId()))).containsExactly(remote.toString());
	}

	@Test
	void filtersCombineAndTheCategoryAlsoAppliesToRemoteGigs() throws Exception {
		LocalityInfo[] places = twoZones();
		UUID photoFirst = publish(api.onSite().locality(places[0].id()));
		publish(api.onSite().locality(places[1].id()));
		publish(api.onSite().category(api.category("anchor")).locality(places[0].id()));
		UUID editorRemote = publish(api.remote());
		publish(api.remote().category(api.category("content-writer")));

		String zone = "zoneId=" + places[0].zoneId();
		assertThat(ids(list(null, zone + "&categoryId=" + api.category("photographer")))).containsExactly(photoFirst.toString());
		assertThat(ids(list(null, zone + "&categoryId=" + api.category("photographer") + "&categoryId=" + api.category("video-editor"))))
				.containsExactlyElementsOf(strings(editorRemote, photoFirst));
		assertThat(ids(list(null, "cityId=" + reference.liveCities().get(0).id() + "&" + zone + "&categoryId=" + api.category("video-editor"))))
				.containsExactly(editorRemote.toString());
	}

	@Test
	void tooManyFilterValuesAreRefused() throws Exception {
		String many = java.util.stream.IntStream.range(0, 51).mapToObj(i -> "categoryId=" + UUID.randomUUID()).collect(Collectors.joining("&"));
		list(null, many).andExpect(status().isBadRequest());
		list(null, "categoryId=nope").andExpect(status().isBadRequest());
	}

	// ---- paging -----------------------------------------------------------------------------------------------------

	@Test
	void theDefaultPageHasTwentyGigsAndAnotherPageFollows() throws Exception {
		List<UUID> all = new ArrayList<>();
		for (int i = 0; i < 23; i++) {
			all.add(publish(api.onSite()));
		}
		java.util.Collections.reverse(all);

		ResultActions first = list(null, "").andExpect(jsonPath("$.items", hasSize(20))).andExpect(jsonPath("$.nextCursor").isNotEmpty());
		String cursor = nextCursor(first.andReturn().getResponse().getContentAsString());
		List<String> second = ids(list(null, "cursor=" + cursor));

		assertThat(second).containsExactlyElementsOf(strings(all.subList(20, 23).toArray(UUID[]::new)));
		list(null, "cursor=" + cursor).andExpect(jsonPath("$.nextCursor").doesNotExist());
	}

	@Test
	void thePageSizeIsBetweenOneAndFifty() throws Exception {
		for (int i = 0; i < 3; i++) {
			publish(api.onSite());
		}

		list(null, "size=1").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.nextCursor").isNotEmpty());
		list(null, "size=50").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(3)));
		list(null, "size=0").andExpect(status().isBadRequest());
		list(null, "size=51").andExpect(status().isBadRequest());
		list(null, "size=-3").andExpect(status().isBadRequest());
		list(null, "size=abc").andExpect(status().isBadRequest());
	}

	@Test
	void walkingThePagesVisitsEveryGigOnceInOrder() throws Exception {
		List<UUID> all = new ArrayList<>();
		for (int i = 0; i < 11; i++) {
			all.add(publish(i % 3 == 0 ? api.remote() : api.onSite()));
		}
		java.util.Collections.reverse(all);

		assertThat(walk(null, "size=3")).containsExactlyElementsOf(strings(all.toArray(UUID[]::new)));
	}

	@Test
	void gigsPublishedAtTheSameInstantAreStillPagedWithoutRepeatsOrGaps() throws Exception {
		List<UUID> all = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			all.add(api.publishOk(poster, api.onSite())); // the test clock does not move: every gig has the same published_at
		}
		assertThat(jdbc.sql("SELECT count(DISTINCT published_at) FROM gigs.gig").query(Long.class).single()).isEqualTo(1);

		List<String> seen = walk(null, "size=2");

		assertThat(seen).hasSize(9).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(strings(all.toArray(UUID[]::new)));
		List<String> byIdDescending = all.stream().map(UUID::toString).sorted(java.util.Comparator.reverseOrder()).toList();
		assertThat(seen).containsExactlyElementsOf(byIdDescending);
	}

	@Test
	void aGigPublishedBetweenPageOneAndPageTwoCausesNoRepeatAndNoGap() throws Exception {
		List<UUID> before = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			before.add(publish(api.onSite()));
		}
		java.util.Collections.reverse(before);

		ResultActions firstPage = list(null, "size=3");
		List<String> seen = new ArrayList<>(JsonPath.<List<String>>read(firstPage.andReturn().getResponse().getContentAsString(), "$.items[*].id"));
		String cursor = nextCursor(firstPage.andReturn().getResponse().getContentAsString());

		UUID late = publish(api.onSite()); // newer than everything on page one
		seen.addAll(rest(null, "size=3", cursor));

		assertThat(seen).doesNotHaveDuplicates().containsExactlyElementsOf(strings(before.toArray(UUID[]::new)));
		assertThat(seen).doesNotContain(late.toString());
		assertThat(ids(list(null, "size=1"))).containsExactly(late.toString()); // and the next fresh listing starts with it
	}

	@Test
	void aGigClosedBetweenPagesDisappearsWithoutShiftingTheOthers() throws Exception {
		List<UUID> all = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			all.add(publish(api.onSite()));
		}
		java.util.Collections.reverse(all);
		ResultActions firstPage = list(null, "size=2");
		String cursor = nextCursor(firstPage.andReturn().getResponse().getContentAsString());

		api.cancel(poster, all.get(3), "{}").andExpect(status().isOk());

		assertThat(rest(null, "size=2", cursor)).containsExactlyElementsOf(strings(all.get(2), all.get(4), all.get(5)));
	}

	@Test
	void pagingKeepsItsFiltersWhenTheyAreRepeatedWithTheCursor() throws Exception {
		List<UUID> remotes = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			remotes.add(publish(api.remote()));
			publish(api.onSite());
		}
		java.util.Collections.reverse(remotes);

		assertThat(walk(null, "size=2&zoneId=" + UUID.randomUUID())).containsExactlyElementsOf(strings(remotes.toArray(UUID[]::new)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "garbage", "!!!", "MS4xLm5vdC1hLXV1aWQ", "MS4tMS4w", "" })
	void aCursorThatIsNotOneWeIssuedIsRefused(String cursor) throws Exception {
		if (cursor.isEmpty()) {
			list(null, "cursor=").andExpect(status().isOk());
			return;
		}
		list(null, "cursor=" + cursor).andExpect(status().isBadRequest()).andExpect(content().string(not(containsString(cursor))));
		api.get(poster, "/me/gigs?cursor=" + cursor).andExpect(status().isBadRequest());
	}

	/** Follows nextCursor from the first page and returns every ID seen, in order. */
	private List<String> walk(TestAccount who, String query) throws Exception {
		ResultActions page = list(who, query);
		List<String> seen = new ArrayList<>(JsonPath.<List<String>>read(page.andReturn().getResponse().getContentAsString(), "$.items[*].id"));
		String cursor = nextCursor(page.andReturn().getResponse().getContentAsString());
		return append(seen, who, query, cursor);
	}

	/** Every ID after the page that {@code cursor} points past. */
	private List<String> rest(TestAccount who, String query, String cursor) throws Exception {
		return append(new ArrayList<>(), who, query, cursor);
	}

	private List<String> append(List<String> seen, TestAccount who, String query, String cursor) throws Exception {
		int guard = 0;
		while (cursor != null && guard++ < 100) {
			String body = list(who, query + "&cursor=" + cursor).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			seen.addAll(JsonPath.<List<String>>read(body, "$.items[*].id"));
			cursor = nextCursor(body);
		}
		return seen;
	}

	// ---- the Poster's own gigs ---------------------------------------------------------------------------------------

	@Test
	void thePosterListsOwnGigsInEveryStatusNewestFirstAndNobodyElses() throws Exception {
		UUID open = publish(api.onSite());
		UUID cancelled = publish(api.onSite());
		api.cancel(poster, cancelled, "{\"reason\":\"Venue closed\"}").andExpect(status().isOk());
		UUID expired = publish(api.onSite());
		jdbc.sql("UPDATE gigs.gig SET status = 'EXPIRED', closed_at = now() WHERE id = :id").param("id", expired).update();
		UUID filled = publish(api.remote());
		jdbc.sql("UPDATE gigs.gig SET status = 'FILLED' WHERE id = :id").param("id", filled).update();
		api.publishOk(viewer, api.onSite());

		String body = api.get(poster, "/me/gigs").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactlyElementsOf(strings(filled, expired, cancelled, open));
		assertThat(JsonPath.<List<String>>read(body, "$.items[*].status")).containsExactly("FILLED", "EXPIRED", "CANCELLED", "OPEN");
		assertThat(JsonPath.<String>read(body, "$.items[2].closeReason")).isEqualTo("Venue closed");
		assertThat(JsonPath.<String>read(body, "$.items[2].closedAt")).isNotBlank();
		api.get(viewer, "/me/gigs").andExpect(jsonPath("$.items", hasSize(1)));
	}

	@Test
	void theMyGigsListIsPagedAndNeedsAToken() throws Exception {
		List<UUID> all = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			all.add(publish(api.onSite()));
		}
		java.util.Collections.reverse(all);

		assertThat(walkMine("size=2")).containsExactlyElementsOf(strings(all.toArray(UUID[]::new)));
		api.get(poster, "/me/gigs?size=0").andExpect(status().isBadRequest());
		api.get(poster, "/me/gigs?size=51").andExpect(status().isBadRequest());
		api.get(null, "/me/gigs").andExpect(status().isUnauthorized());
	}

	private List<String> walkMine(String query) throws Exception {
		List<String> seen = new ArrayList<>();
		String cursor = null;
		int guard = 0;
		do {
			String body = api.get(poster, "/me/gigs?" + query + (cursor == null ? "" : "&cursor=" + cursor)).andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			seen.addAll(JsonPath.<List<String>>read(body, "$.items[*].id"));
			cursor = nextCursor(body);
		}
		while (cursor != null && guard++ < 100);
		return seen;
	}

}
