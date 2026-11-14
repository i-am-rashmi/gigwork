package com.gigwork.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.LocalityInfo;
import com.gigwork.core.reference.ReferenceData;

/** The owner's endpoints: the three sections, statuses, and every rule that answers 400. */
@IdentityIntegrationTest
class TalentProfileApiTest {

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
	UUID photographer;
	UUID anchor;
	UUID videographer;

	@BeforeEach
	void setUp() {
		api = new ProfileApiSupport(mvc, reference);
		talent = accounts.create();
		photographer = api.categoryId("photographer");
		anchor = api.categoryId("anchor");
		videographer = api.categoryId("videographer");
	}

	private LocalityInfo indiranagar() {
		return reference.searchLocalities("indiranagar", 1).get(0);
	}

	private String location() {
		return ProfileApiSupport.locationByLocality(indiranagar().id(), 15, true);
	}

	// ---- status ----------------------------------------------------------------------------------------------------

	@Test
	void thereIsNothingToReadBeforeTheFirstSave() throws Exception {
		api.getOwn(talent).andExpect(status().isNotFound());
	}

	@Test
	void theProfileIsIncompleteUntilWorkAndLocationAreSavedThenActive() throws Exception {
		api.put(talent, "work", api.simpleWork(photographer)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INCOMPLETE")).andExpect(jsonPath("$.location").doesNotExist());
		api.put(talent, "portfolio", ProfileApiSupport.links("instagram.com/asha")).andExpect(jsonPath("$.status").value("INCOMPLETE"));
		api.put(talent, "location", location()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.hidden").value(false));
		api.getOwn(talent).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void theOrderOfTheSectionsDoesNotMatter() throws Exception {
		api.put(talent, "location", location()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INCOMPLETE"))
				.andExpect(jsonPath("$.work").doesNotExist());
		api.put(talent, "work", api.simpleWork(photographer)).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void hidingAndShowingAgainReturnsToTheRightStatus() throws Exception {
		api.put(talent, "work", api.simpleWork(photographer));
		api.put(talent, "location", location()).andExpect(jsonPath("$.status").value("ACTIVE"));

		api.put(talent, "visibility", "{\"hidden\":true}").andExpect(jsonPath("$.status").value("HIDDEN")).andExpect(jsonPath("$.hidden").value(true));
		api.put(talent, "portfolio", ProfileApiSupport.links("example.com")).andExpect(jsonPath("$.status").value("HIDDEN")); // saving does not unhide
		api.put(talent, "visibility", "{\"hidden\":false}").andExpect(jsonPath("$.status").value("ACTIVE"));

		TestAccount newcomer = accounts.create();
		api.put(newcomer, "work", api.simpleWork(photographer));
		api.put(newcomer, "visibility", "{\"hidden\":true}").andExpect(jsonPath("$.status").value("HIDDEN"));
		api.put(newcomer, "visibility", "{\"hidden\":false}").andExpect(jsonPath("$.status").value("INCOMPLETE"));
	}

	@Test
	void anAccountCanHideBeforeSavingAnythingElse() throws Exception {
		api.put(talent, "visibility", "{\"hidden\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HIDDEN"))
				.andExpect(jsonPath("$.work").doesNotExist());
	}

	@Test
	void aTokenIsRequiredAndOnlyTheCallersOwnProfileIsTouched() throws Exception {
		api.put(null, "work", api.simpleWork(photographer)).andExpect(status().isUnauthorized());
		api.put(null, "location", location()).andExpect(status().isUnauthorized());
		api.put(null, "portfolio", ProfileApiSupport.links()).andExpect(status().isUnauthorized());
		api.put(null, "visibility", "{\"hidden\":true}").andExpect(status().isUnauthorized());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/me/talent-profile")).andExpect(status().isUnauthorized());

		TestAccount other = accounts.create();
		api.put(talent, "work", api.simpleWork(photographer));
		api.getOwn(other).andExpect(status().isNotFound());
	}

	// ---- work section ----------------------------------------------------------------------------------------------

	@Test
	void theWorkSectionNeedsAFirstNameButTheOtherSectionsDoNot() throws Exception {
		TestAccount unnamed = accounts.createWithoutName();

		api.put(unnamed, "work", api.simpleWork(photographer)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Add your first name before setting up your profile"));
		api.getOwn(unnamed).andExpect(status().isNotFound()); // nothing was saved

		api.put(unnamed, "location", location()).andExpect(status().isOk());
		api.put(unnamed, "portfolio", ProfileApiSupport.links("example.com")).andExpect(status().isOk());
		api.put(unnamed, "work", api.simpleWork(photographer)).andExpect(status().isBadRequest());

		accounts.setFirstName(unnamed.id(), "Asha");
		api.put(unnamed, "work", api.simpleWork(photographer)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void aBlankStoredNameDoesNotCount() throws Exception {
		TestAccount blank = accounts.createWithoutName();
		accounts.setFirstName(blank.id(), "   ");

		api.put(blank, "work", api.simpleWork(photographer)).andExpect(status().isBadRequest());
	}

	@Test
	void savesAndReturnsTheWorkSection() throws Exception {
		UUID wedding = api.specialisationId("photographer", "wedding");
		UUID candid = api.specialisationId("photographer", "candid");
		String body = api.work("GROUP", 4, "  Studio of four  ", "We shoot weddings.", List.of(photographer, anchor),
				List.of(wedding, candid), List.of("en", "kn"));

		api.put(talent, "work", body).andExpect(status().isOk()).andExpect(jsonPath("$.work.profileType").value("GROUP"))
				.andExpect(jsonPath("$.work.memberCount").value(4)).andExpect(jsonPath("$.work.headline").value("Studio of four"))
				.andExpect(jsonPath("$.work.bio").value("We shoot weddings."))
				.andExpect(jsonPath("$.work.categories[*].slug", contains("photographer", "anchor")))
				.andExpect(jsonPath("$.work.specialisations[*].slug", contains("wedding", "candid")))
				.andExpect(jsonPath("$.work.languages[*].code", contains("en", "kn")));
	}

	@Test
	void savingAgainReplacesTheWholeSection() throws Exception {
		api.put(talent, "work", api.work("GROUP", 3, "First", "bio", List.of(photographer, anchor),
				List.of(api.specialisationId("photographer", "wedding")), List.of("en")));

		api.put(talent, "work", api.work("INDIVIDUAL", null, "Second", null, List.of(videographer), List.of(), List.of()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.work.profileType").value("INDIVIDUAL"))
				.andExpect(jsonPath("$.work.memberCount").doesNotExist()).andExpect(jsonPath("$.work.bio").doesNotExist())
				.andExpect(jsonPath("$.work.categories", hasSize(1))).andExpect(jsonPath("$.work.categories[0].slug").value("videographer"))
				.andExpect(jsonPath("$.work.specialisations", hasSize(0))).andExpect(jsonPath("$.work.languages", hasSize(0)));
		assertThat(jdbc.sql("SELECT count(*) FROM profile.talent_category WHERE account_id = :a").param("a", talent.id()).query(Integer.class).single()).isEqualTo(1);
		assertThat(jdbc.sql("SELECT count(*) FROM profile.talent_specialisation WHERE account_id = :a").param("a", talent.id()).query(Integer.class).single()).isZero();
	}

	@Test
	void aGroupMemberCountIsOptionalAndBetween2And50() throws Exception {
		for (int ok : new int[] { 2, 50 }) {
			api.put(talent, "work", api.work("GROUP", ok, "Crew", null, List.of(photographer), List.of(), List.of())).andExpect(status().isOk());
		}
		api.put(talent, "work", api.work("GROUP", null, "Crew", null, List.of(photographer), List.of(), List.of())).andExpect(status().isOk());
		for (int bad : new int[] { 1, 0, -3, 51 }) {
			api.put(talent, "work", api.work("GROUP", bad, "Crew", null, List.of(photographer), List.of(), List.of())).andExpect(status().isBadRequest());
		}
		api.put(talent, "work", api.work("INDIVIDUAL", 3, "Solo", null, List.of(photographer), List.of(), List.of()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void categoriesMustBeOneToFiveLiveAndDistinct() throws Exception {
		List<UUID> six = reference.liveCategories().stream().limit(6).map(c -> c.id()).toList();
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, six.subList(0, 5), List.of(), List.of())).andExpect(status().isOk());
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, six, List.of(), List.of())).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Choose 1 to 5 categories."));
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, List.of(), List.of(), List.of())).andExpect(status().isBadRequest());
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, List.of(photographer, photographer), List.of(), List.of()))
				.andExpect(status().isBadRequest());
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, List.of(UUID.randomUUID()), List.of(), List.of()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aSpecialisationMustBelongToOneOfTheChosenCategories() throws Exception {
		UUID weddingFilms = api.specialisationId("videographer", "wedding-films");

		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, List.of(photographer), List.of(weddingFilms), List.of()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Each specialisation must belong to one of your chosen categories."));
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, List.of(photographer, videographer), List.of(weddingFilms), List.of()))
				.andExpect(status().isOk());
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, List.of(photographer), List.of(UUID.randomUUID()), List.of()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void atMostTenDistinctSpecialisations() throws Exception {
		List<UUID> eleven = java.util.stream.Stream.concat(api.category("photographer").specialisations().stream(),
				api.category("videographer").specialisations().stream()).map(s -> s.id()).limit(11).toList();
		List<UUID> ten = eleven.subList(0, 10);

		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, List.of(photographer, videographer), ten, List.of())).andExpect(status().isOk());
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, List.of(photographer, videographer), eleven, List.of()))
				.andExpect(status().isBadRequest());
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, List.of(photographer), List.of(ten.get(0), ten.get(0)), List.of()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void languagesNeedALanguageRelevantCategoryAndALiveLanguage() throws Exception {
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, List.of(photographer), List.of(), List.of("kn")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Languages can only be listed")));
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, List.of(photographer, anchor), List.of(), List.of("kn", "hi")))
				.andExpect(status().isOk());
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, List.of(anchor), List.of(), List.of("xx")))
				.andExpect(status().isBadRequest());
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, List.of(anchor), List.of(), List.of("kn", "kn")))
				.andExpect(status().isBadRequest());
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", null, List.of(photographer), List.of(), List.of())).andExpect(status().isOk());
	}

	@Test
	void headlineAndBioHaveLimitsAndNoContactDetails() throws Exception {
		api.put(talent, "work", api.work("INDIVIDUAL", null, "x".repeat(80), "y".repeat(1000), List.of(photographer), List.of(), List.of()))
				.andExpect(status().isOk());
		api.put(talent, "work", api.work("INDIVIDUAL", null, "x".repeat(81), null, List.of(photographer), List.of(), List.of())).andExpect(status().isBadRequest());
		api.put(talent, "work", api.work("INDIVIDUAL", null, "H", "y".repeat(1001), List.of(photographer), List.of(), List.of())).andExpect(status().isBadRequest());
		api.put(talent, "work", api.work("INDIVIDUAL", null, "  ", null, List.of(photographer), List.of(), List.of())).andExpect(status().isBadRequest());
		api.put(talent, "work", api.work(null, null, "H", null, List.of(photographer), List.of(), List.of())).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = { "Call 98450 12345", "+91-9845012345", "09845012345", "a@b.co", "Write to me: asha@example.com", "wa.me/9198450",
			"t.me/asha", "tel:9845012345" })
	void contactDetailsInTheHeadlineOrBioAreRefused(String text) throws Exception {
		String message = "Contact details can't be in your profile. Posters reach you through GigWork.";

		api.put(talent, "work", api.work("INDIVIDUAL", null, text, null, List.of(photographer), List.of(), List.of()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(message))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("98450"))));
		api.put(talent, "work", api.work("INDIVIDUAL", null, "Fine headline", text, List.of(photographer), List.of(), List.of()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(message));
		api.getOwn(talent).andExpect(status().isNotFound()); // nothing was saved
	}

	@Test
	void ordinaryNumbersInTheTextAreFine() throws Exception {
		api.put(talent, "work", api.work("INDIVIDUAL", null, "Since 2015, 300+ weddings", "From Rs 25000, 4.9 rating.",
				List.of(photographer), List.of(), List.of())).andExpect(status().isOk());
	}

	// ---- location section -------------------------------------------------------------------------------------------

	@Test
	void aChosenLocalityGivesCoordinatesAndLabelFromReferenceData() throws Exception {
		LocalityInfo locality = indiranagar();

		api.put(talent, "location", ProfileApiSupport.locationByLocality(locality.id(), 20, true)).andExpect(status().isOk())
				.andExpect(jsonPath("$.location.label").value(locality.name())).andExpect(jsonPath("$.location.cityName").value("Bengaluru"))
				.andExpect(jsonPath("$.location.zoneName").isNotEmpty()).andExpect(jsonPath("$.location.localityId").value(locality.id().toString()))
				.andExpect(jsonPath("$.location.serviceRadiusKm").value(20)).andExpect(jsonPath("$.location.willingToTravel").value(true));

		var row = jdbc.sql("SELECT lat, lng, locality_id, city_id, zone_id FROM profile.talent_profile WHERE account_id = :a")
				.param("a", talent.id()).query((rs, n) -> new Object[] { rs.getDouble("lat"), rs.getDouble("lng"),
						rs.getObject("locality_id", UUID.class), rs.getObject("city_id", UUID.class), rs.getObject("zone_id", UUID.class) })
				.single();
		assertThat(row[0]).isEqualTo(locality.lat());
		assertThat(row[1]).isEqualTo(locality.lng());
		assertThat(row[2]).isEqualTo(locality.id());
		assertThat(row[3]).isEqualTo(reference.resolveArea(locality.lat(), locality.lng()).cityId());
		assertThat(row[4]).isEqualTo(reference.resolveArea(locality.lat(), locality.lng()).zoneId());
	}

	@Test
	void aMapPointIsRoundedToThreeDecimalsAndLabelledWithTheNearestLocality() throws Exception {
		LocalityInfo locality = indiranagar();
		double lat = locality.lat() + 0.0004321;
		double lng = locality.lng() - 0.0004321;

		api.put(talent, "location", ProfileApiSupport.locationByPoint(lat, lng, 10, false)).andExpect(status().isOk())
				.andExpect(jsonPath("$.location.label").isNotEmpty()).andExpect(jsonPath("$.location.localityId").doesNotExist());

		var stored = jdbc.sql("SELECT lat, lng, location_label FROM profile.talent_profile WHERE account_id = :a").param("a", talent.id())
				.query((rs, n) -> new Object[] { rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("location_label") }).single();
		assertThat((double) stored[0]).isEqualTo(Math.round(lat * 1000) / 1000.0);
		assertThat((double) stored[1]).isEqualTo(Math.round(lng * 1000) / 1000.0);
		assertThat(reference.nearestLocality((double) stored[0], (double) stored[1], 2000).map(LocalityInfo::name)).contains((String) stored[2]);
	}

	@Test
	void aPointFarFromEveryLocalityIsLabelledWithTheZoneName() throws Exception {
		api.put(talent, "location", ProfileApiSupport.locationByPoint(13.350, 77.570, 25, true)).andExpect(status().isOk())
				.andExpect(jsonPath("$.location.label").value("North")); // 20+ km north of the city: no locality within 2 km
	}

	@Test
	void aPointMoreThan50KmFromTheNearestZoneIsRefusedAndOneInsideTheLimitIsAccepted() throws Exception {
		api.put(talent, "location", ProfileApiSupport.locationByPoint(13.550, 77.570, 25, true)).andExpect(status().isOk()); // about 45 km
		api.put(accounts.create(), "location", ProfileApiSupport.locationByPoint(13.650, 77.570, 25, true)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("more than 50 km")));
		api.put(accounts.create(), "location", ProfileApiSupport.locationByPoint(28.61, 77.21, 25, true)).andExpect(status().isBadRequest());
	}

	@Test
	void exactlyOneOfLocalityAndPointIsRequired() throws Exception {
		api.put(talent, "location", "{\"localityId\":\"" + indiranagar().id() + "\",\"point\":{\"lat\":12.97,\"lng\":77.6},\"serviceRadiusKm\":5,\"willingToTravel\":true}")
				.andExpect(status().isBadRequest());
		api.put(talent, "location", "{\"serviceRadiusKm\":5,\"willingToTravel\":true}").andExpect(status().isBadRequest());
		api.put(talent, "location", "{\"point\":{\"lat\":12.97},\"serviceRadiusKm\":5,\"willingToTravel\":true}").andExpect(status().isBadRequest());
		api.put(talent, "location", ProfileApiSupport.locationByLocality(UUID.randomUUID(), 5, true)).andExpect(status().isBadRequest());
	}

	@Test
	void radiusIsOneToFiftyAndRequired() throws Exception {
		for (int radius : new int[] { 0, -1, 51 }) {
			api.put(talent, "location", ProfileApiSupport.locationByLocality(indiranagar().id(), radius, true)).andExpect(status().isBadRequest());
		}
		for (int radius : new int[] { 1, 50 }) {
			api.put(talent, "location", ProfileApiSupport.locationByLocality(indiranagar().id(), radius, true)).andExpect(status().isOk());
		}
		api.put(talent, "location", "{\"localityId\":\"" + indiranagar().id() + "\",\"willingToTravel\":true}").andExpect(status().isBadRequest());
		api.put(talent, "location", "{\"localityId\":\"" + indiranagar().id() + "\",\"serviceRadiusKm\":5}").andExpect(status().isBadRequest());
	}

	@Test
	void pointsOutsideTheEarthAreRefused() throws Exception {
		api.put(talent, "location", ProfileApiSupport.locationByPoint(95, 77, 5, true)).andExpect(status().isBadRequest());
		api.put(talent, "location", ProfileApiSupport.locationByPoint(12, 190, 5, true)).andExpect(status().isBadRequest());
	}

	@Test
	void theOwnersViewNeverHasCoordinates() throws Exception {
		api.put(talent, "work", api.simpleWork(photographer));
		api.put(talent, "location", ProfileApiSupport.locationByPoint(12.9716, 77.5946, 10, true));

		String body = api.getOwn(talent).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("\"lat\"").doesNotContain("\"lng\"").doesNotContain("12.97").doesNotContain("77.59");
	}

	// ---- portfolio section --------------------------------------------------------------------------------------------

	@Test
	void savesLinksInOrderWithTypesAndTheSchemeAdded() throws Exception {
		api.put(talent, "portfolio", ProfileApiSupport.links("instagram.com/asha", "https://youtu.be/abc", "asha.in", "behance.net/asha",
				"vimeo.com/1", "soundcloud.com/asha", "facebook.com/asha"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.portfolio[*].url", contains("https://instagram.com/asha", "https://youtu.be/abc", "https://asha.in",
						"https://behance.net/asha", "https://vimeo.com/1", "https://soundcloud.com/asha", "https://facebook.com/asha")))
				.andExpect(jsonPath("$.portfolio[*].type", contains("INSTAGRAM", "YOUTUBE", "WEBSITE", "BEHANCE", "VIMEO", "AUDIO", "SOCIAL")));

		api.put(talent, "portfolio", ProfileApiSupport.links("asha.in", "instagram.com/asha")).andExpect(jsonPath("$.portfolio[*].type", contains("WEBSITE", "INSTAGRAM")));
		api.put(talent, "portfolio", ProfileApiSupport.links()).andExpect(status().isOk()).andExpect(jsonPath("$.portfolio", hasSize(0)));
	}

	@Test
	void atMostTenLinksAndNoDuplicates() throws Exception {
		String[] ten = java.util.stream.IntStream.range(0, 10).mapToObj(i -> "example" + i + ".com").toArray(String[]::new);
		api.put(talent, "portfolio", ProfileApiSupport.links(ten)).andExpect(status().isOk());
		String[] eleven = java.util.stream.IntStream.range(0, 11).mapToObj(i -> "example" + i + ".com").toArray(String[]::new);
		api.put(talent, "portfolio", ProfileApiSupport.links(eleven)).andExpect(status().isBadRequest());

		api.put(talent, "portfolio", ProfileApiSupport.links("example.com", "https://EXAMPLE.com/")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("The same portfolio link is listed twice."));
		api.put(talent, "portfolio", ProfileApiSupport.links("example.com/a", "example.com/b")).andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = { "bit.ly/x", "https://tinyurl.com/x", "wa.me/91", "t.me/x", "tel:+91", "mailto:a@b.co", "http://localhost/x",
			"http://10.1.2.3", "ftp://example.com", "javascript:alert(1)", "example" })
	void aBadLinkRejectsTheWholeSaveAndChangesNothing(String link) throws Exception {
		api.put(talent, "portfolio", ProfileApiSupport.links("keepme.example.com")).andExpect(status().isOk());

		api.put(talent, "portfolio", ProfileApiSupport.links("fine.example.com", link)).andExpect(status().isBadRequest());

		api.getOwn(talent).andExpect(jsonPath("$.portfolio[*].url", contains("https://keepme.example.com")));
	}

	@Test
	void aLinkLongerThan2048CharactersIsRefused() throws Exception {
		api.put(talent, "portfolio", ProfileApiSupport.links("example.com/" + "a".repeat(2100))).andExpect(status().isBadRequest());
	}

	@Test
	void theLinkTypesComeFromTheServerNotTheClient() throws Exception {
		api.put(talent, "portfolio", "{\"links\":[\"example.com\"],\"types\":[\"INSTAGRAM\"]}").andExpect(status().isOk())
				.andExpect(jsonPath("$.portfolio[0].type").value("WEBSITE"));
	}

	@Test
	void theOwnerSeesAllTheirDataInOneResponse() throws Exception {
		api.put(talent, "work", api.work("GROUP", 5, "Crew", "Bio", List.of(photographer, anchor), List.of(api.specialisationId("photographer", "wedding")), List.of("en")));
		api.put(talent, "location", location());
		api.put(talent, "portfolio", ProfileApiSupport.links("instagram.com/crew"));

		api.getOwn(talent).andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(talent.id().toString()))
				.andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.work.categories[*].slug", containsInAnyOrder("photographer", "anchor")))
				.andExpect(jsonPath("$.location.label").isNotEmpty()).andExpect(jsonPath("$.portfolio", hasSize(1)));
	}

}
