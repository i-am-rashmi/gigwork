package com.gigwork.core.gigs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.gigs.GigApiSupport.Gig;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.LocalityInfo;
import com.gigwork.core.reference.ReferenceData;

/** Publishing a gig (FR-15, FR-16): what is stored, what is shown back, and every rule that answers 400. */
@IdentityIntegrationTest
@RecordApplicationEvents
class GigPublishApiTest {

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
	@Autowired
	ApplicationEvents events;

	GigApiSupport api;
	TestAccount poster;

	@BeforeEach
	void setUp() {
		clock.reset();
		api = new GigApiSupport(mvc, reference, clock);
		poster = accounts.create();
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	private Map<String, Object> row(String id) {
		return jdbc.sql("SELECT * FROM gigs.gig WHERE id = :id").param("id", UUID.fromString(id)).query().singleRow();
	}

	private String publishedId(Gig gig) throws Exception {
		return com.jayway.jsonpath.JsonPath.read(api.publish(poster, gig).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString(), "$.id");
	}

	// ---- what a published gig looks like ------------------------------------------------------------------------

	@Test
	void anOnSiteGigChosenByLocalityIsOpenAndLocatedAtTheLocality() throws Exception {
		LocalityInfo locality = reference.searchLocalities("indiranagar", 1).get(0);
		Gig gig = api.onSite().specialisation(api.specialisation("photographer", "wedding")).budget("15000", "25000");

		String body = api.publish(poster, gig).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.title").value("Photographer for a two-day wedding"))
				.andExpect(jsonPath("$.workMode").value("ON_SITE")).andExpect(jsonPath("$.locationLabel").value(locality.name()))
				.andExpect(jsonPath("$.cityName").value("Bengaluru")).andExpect(jsonPath("$.taOffered").value(true))
				.andExpect(jsonPath("$.budgetMin").value(15000)).andExpect(jsonPath("$.budgetMax").value(25000))
				.andExpect(jsonPath("$.specialisationName").value("Wedding")).andExpect(jsonPath("$.categoryName").value("Photographer"))
				.andExpect(jsonPath("$.startAt").value(gig.start().toString()))
				.andExpect(jsonPath("$.posterFirstName").value("Tester")).andReturn().getResponse().getContentAsString();

		Map<String, Object> stored = row(com.jayway.jsonpath.JsonPath.read(body, "$.id"));
		assertThat(stored.get("poster_id")).isEqualTo(poster.id());
		assertThat(stored.get("status")).isEqualTo("OPEN");
		assertThat((double) stored.get("lat")).isEqualTo(locality.lat());
		assertThat((double) stored.get("lng")).isEqualTo(locality.lng());
		assertThat(stored.get("locality_id")).isEqualTo(locality.id());
		assertThat(stored.get("closed_at")).isNull();
		assertThat(stored.get("location")).isNotNull();
	}

	@Test
	void anOnSiteGigChosenByMapPointIsStoredRoundedToThreeDecimals() throws Exception {
		String id = publishedId(api.onSite().point(12.97649, 77.60451));

		Map<String, Object> stored = row(id);
		assertThat((double) stored.get("lat")).isEqualTo(12.976);
		assertThat((double) stored.get("lng")).isEqualTo(77.605);
		assertThat(stored.get("locality_id")).isNull();
		assertThat(stored.get("city_id")).isNotNull();
		assertThat(stored.get("zone_id")).isNotNull();
		assertThat((String) stored.get("location_label")).isNotBlank();
	}

	@Test
	void aRemoteGigHasNoLocationAndNoTravelAllowance() throws Exception {
		String id = publishedId(api.remote().language(null));

		api.get(poster, "/gigs/" + id);
		Map<String, Object> stored = row(id);
		assertThat(stored.get("work_mode")).isEqualTo("REMOTE");
		assertThat(stored.get("lat")).isNull();
		assertThat(stored.get("city_id")).isNull();
		assertThat(stored.get("location_label")).isNull();
		assertThat(stored.get("ta_offered")).isNull();
		assertThat(stored.get("location")).isNull();
	}

	@Test
	void theResponseNeverContainsCoordinatesOrAnApplicantCount() throws Exception {
		api.publish(poster, api.onSite()).andExpect(status().isCreated())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.anyOf(
						org.hamcrest.Matchers.containsString("lat"), org.hamcrest.Matchers.containsString("lng"),
						org.hamcrest.Matchers.containsString("point"), org.hamcrest.Matchers.containsString("pplicant")))));
	}

	@Test
	void aBudgetCanBeAFixedAmountOrLeftOutAndWholeNumbersWrittenWithDecimalsAreAccepted() throws Exception {
		assertThat(row(publishedId(api.onSite().budget("5000", "5000"))).get("budget_min")).isEqualTo(5000L);
		assertThat(row(publishedId(api.onSite().budget("5000.00", "6000.0"))).get("budget_max")).isEqualTo(6000L);
		Map<String, Object> none = row(publishedId(api.onSite()));
		assertThat(none.get("budget_min")).isNull();
		assertThat(none.get("budget_max")).isNull();
	}

	@Test
	void aCategoryThatAllowsBothWorkModesAcceptsEither() throws Exception {
		UUID both = api.category("painter-caricature");
		api.publish(poster, api.onSite().category(both)).andExpect(status().isCreated());
		api.publish(poster, api.remote().category(both)).andExpect(status().isCreated());
	}

	@Test
	void aLanguageIsAcceptedInALanguageCategory() throws Exception {
		api.publish(poster, api.onSite().category(api.category("anchor")).language("hi")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.languageCode").value("hi")).andExpect(jsonPath("$.languageName").value("Hindi"));
	}

	@Test
	void publishingWritesOneGigPublishedEventWithWhatConsumersNeed() throws Exception {
		Gig gig = api.onSite();
		String id = publishedId(gig);

		var published = events.stream(GigPublished.class).toList();
		assertThat(published).hasSize(1);
		GigPublished event = published.get(0);
		assertThat(event.gigId()).isEqualTo(UUID.fromString(id));
		assertThat(event.posterId()).isEqualTo(poster.id());
		assertThat(event.categoryId()).isEqualTo(api.category("photographer"));
		assertThat(event.workMode()).isEqualTo("ON_SITE");
		assertThat(event.zoneId()).isNotNull();
		assertThat(event.cityId()).isNotNull();
		assertThat(event.startAt()).isEqualTo(gig.start());
	}

	@Test
	void aRefusedGigWritesNothingAndNoEvent() throws Exception {
		long before = jdbc.sql("SELECT count(*) FROM gigs.gig").query(Long.class).single();

		api.publish(poster, api.onSite().title("")).andExpect(status().isBadRequest());

		assertThat(jdbc.sql("SELECT count(*) FROM gigs.gig").query(Long.class).single()).isEqualTo(before);
		assertThat(events.stream(GigPublished.class).count()).isZero();
	}

	// ---- who may publish ------------------------------------------------------------------------------------------

	@Test
	void publishingNeedsAToken() throws Exception {
		api.publish(null, api.onSite()).andExpect(status().isUnauthorized());
	}

	@Test
	void aPosterWithoutAFirstNameCannotPublish() throws Exception {
		api.publish(accounts.createWithoutName(), api.onSite()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("First name required"));
	}

	// ---- category, specialisation, language -----------------------------------------------------------------------

	@Test
	void anUnknownCategoryIsRefused() throws Exception {
		api.publish(poster, api.onSite().category(UUID.randomUUID())).andExpect(status().isBadRequest());
	}

	@Test
	void aCategoryThatAllowsOnlyOnSiteRefusesARemoteGig() throws Exception {
		api.publish(poster, api.remote().category(api.category("photographer"))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("remote")));
	}

	@Test
	void aCategoryThatAllowsOnlyRemoteRefusesAnOnSiteGig() throws Exception {
		api.publish(poster, api.onSite().category(api.category("video-editor"))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("on-site")));
	}

	@Test
	void aSpecialisationOfAnotherCategoryOrUnknownIsRefused() throws Exception {
		api.publish(poster, api.onSite().specialisation(api.specialisation("videographer", "wedding-films"))).andExpect(status().isBadRequest());
		api.publish(poster, api.onSite().specialisation(UUID.randomUUID())).andExpect(status().isBadRequest());
	}

	@Test
	void aLanguageOutsideALanguageCategoryOrUnknownIsRefused() throws Exception {
		api.publish(poster, api.onSite().language("hi")).andExpect(status().isBadRequest());
		api.publish(poster, api.onSite().category(api.category("anchor")).language("zz")).andExpect(status().isBadRequest());
	}

	// ---- location and travel allowance ----------------------------------------------------------------------------

	@Test
	void anOnSiteGigNeedsALocation() throws Exception {
		api.publish(poster, api.onSite().noLocation()).andExpect(status().isBadRequest());
	}

	@Test
	void aLocalityAndAMapPointTogetherAreRefused() throws Exception {
		api.publish(poster, api.onSite().extra("\"location\":{\"localityId\":\"" + api.indiranagar()
				+ "\",\"point\":{\"lat\":12.97,\"lng\":77.6}}").noLocation()).andExpect(status().isBadRequest());
	}

	@Test
	void anUnknownLocalityAndAPointFarFromEveryZoneAreRefused() throws Exception {
		api.publish(poster, api.onSite().locality(UUID.randomUUID())).andExpect(status().isBadRequest());
		api.publish(poster, api.onSite().point(28.61, 77.21)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("50 km")));
	}

	@Test
	void aPointOutOfRangeOrWithOneCoordinateIsRefused() throws Exception {
		api.publish(poster, api.onSite().point(95, 77.6)).andExpect(status().isBadRequest());
		api.publish(poster, api.onSite().noLocation().extra("\"location\":{\"point\":{\"lat\":12.97}}")).andExpect(status().isBadRequest());
	}

	@Test
	void anOnSiteGigNeedsTheTravelAllowanceFlag() throws Exception {
		api.publish(poster, api.onSite().ta(null)).andExpect(status().isBadRequest());
	}

	@Test
	void aRemoteGigWithALocationOrATravelAllowanceIsRefused() throws Exception {
		api.publish(poster, api.remote().locality(api.indiranagar())).andExpect(status().isBadRequest());
		api.publish(poster, api.remote().point(12.97, 77.6)).andExpect(status().isBadRequest());
		api.publish(poster, api.remote().ta(false)).andExpect(status().isBadRequest());
	}

	// ---- dates ---------------------------------------------------------------------------------------------------

	@Test
	void aGigMustStartInTheFuture() throws Exception {
		api.publish(poster, api.onSite().startsIn(Duration.ofMinutes(-5)).lasting(Duration.ofHours(2))).andExpect(status().isBadRequest());
		api.publish(poster, api.onSite().startsIn(Duration.ofSeconds(-1)).lasting(Duration.ofHours(2))).andExpect(status().isBadRequest());
	}

	@Test
	void aGigCanStartAtMostOneYearAhead() throws Exception {
		api.publish(poster, api.onSite().startsIn(Duration.ofDays(364))).andExpect(status().isCreated());
		api.publish(poster, api.onSite().startsIn(Duration.ofDays(367))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("12 months")));
	}

	@Test
	void aGigMustEndAfterItStartsAndLastAtMostFourteenDays() throws Exception {
		api.publish(poster, api.onSite().lasting(Duration.ZERO)).andExpect(status().isBadRequest());
		api.publish(poster, api.onSite().lasting(Duration.ofHours(-1))).andExpect(status().isBadRequest());
		api.publish(poster, api.onSite().lasting(Duration.ofDays(14))).andExpect(status().isCreated());
		api.publish(poster, api.onSite().lasting(Duration.ofDays(14).plusSeconds(1))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("14 days")));
	}

	@Test
	void aMissingOrMalformedDateIsRefused() throws Exception {
		api.publish(poster, api.onSite().start(null)).andExpect(status().isBadRequest());
		api.publish(poster, api.onSite().extra("\"startAt\":\"tomorrow\"")).andExpect(status().isBadRequest());
	}

	// ---- budget ----------------------------------------------------------------------------------------------------

	@ParameterizedTest
	@ValueSource(strings = { "-1,100", "500,100", "1500.5,2000", "1000,2000.25", "1000,null", "null,1000" })
	void aBrokenBudgetIsRefused(String pair) throws Exception {
		String[] parts = pair.split(",");
		api.publish(poster, api.onSite().budget(parts[0], parts[1])).andExpect(status().isBadRequest());
	}

	// ---- text ------------------------------------------------------------------------------------------------------

	@Test
	void theTitleAndDescriptionHaveLengthLimits() throws Exception {
		api.publish(poster, api.onSite().title("x".repeat(100))).andExpect(status().isCreated());
		api.publish(poster, api.onSite().title("x".repeat(101))).andExpect(status().isBadRequest());
		api.publish(poster, api.onSite().title("   ")).andExpect(status().isBadRequest());
		api.publish(poster, api.onSite().description("y".repeat(2000))).andExpect(status().isCreated());
		api.publish(poster, api.onSite().description("y".repeat(2001))).andExpect(status().isBadRequest());
		api.publish(poster, api.onSite().description("")).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = { "Call me on 9876543210", "mail asha@example.com", "chat on wa.me/919876543210",
			"t.me/asha_photos", "+91 98765 43210 for details" })
	void contactDetailsInTheTitleOrTheDescriptionAreRefused(String text) throws Exception {
		api.publish(poster, api.onSite().title(text)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("title")))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(text))));
		api.publish(poster, api.onSite().description(text)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("description")));
	}

	@Test
	void aBodyThatIsNotAGigIsRefused() throws Exception {
		api.publish(poster, "{}").andExpect(status().isBadRequest());
		api.publish(poster, "not json").andExpect(status().isBadRequest());
		api.publish(poster, api.onSite().extra("\"workMode\":\"UNDERWATER\"")).andExpect(status().isBadRequest());
	}

}
