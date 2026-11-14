package com.gigwork.core.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.profile.ProfileApiSupport;
import com.gigwork.core.reference.ReferenceData;

/** The Talent's own endpoints: busy blocks and the calendar (FR-7). The clock is fixed so dates are literal. */
@IdentityIntegrationTest
class BusyBlockApiTest {

	private static final Instant NOW = Instant.parse("2026-11-14T06:30:00Z");

	@Autowired
	MockMvc mvc;
	@Autowired
	MutableClock clock;
	@Autowired
	TestAccounts accounts;
	@Autowired
	ReferenceData reference;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	Availability availability;

	ProfileApiSupport profile;
	TestAccount talent;

	@BeforeEach
	void setUp() throws Exception {
		clock.set(NOW);
		profile = new ProfileApiSupport(mvc, reference);
		talent = withLocation(accounts.create());
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	private TestAccount withLocation(TestAccount account) throws Exception {
		profile.put(account, "work", profile.simpleWork(profile.categoryId("photographer"))).andExpect(status().isOk());
		profile.put(account, "location", ProfileApiSupport.locationByLocality(reference.searchLocalities("indiranagar", 1).get(0).id(), 10, true))
				.andExpect(status().isOk());
		return account;
	}

	private ResultActions add(TestAccount account, String json) throws Exception {
		var request = post("/v1/me/busy-blocks").contentType(MediaType.APPLICATION_JSON).content(json);
		if (account != null) {
			request.header("Authorization", account.header());
		}
		return mvc.perform(request);
	}

	private ResultActions calendar(TestAccount account, String from, String to) throws Exception {
		return mvc.perform(get("/v1/me/calendar").param("from", from).param("to", to).header("Authorization", account.header()));
	}

	private static String timed(String start, String end) {
		return "{\"start\":\"" + start + "\",\"end\":\"" + end + "\"}";
	}

	private static String allDay(String first, String last) {
		return "{\"allDay\":true,\"startDate\":\"" + first + "\",\"endDate\":\"" + last + "\"}";
	}

	// ---- adding -------------------------------------------------------------------------------------------------------

	@Test
	void aTimedBlockKeepsItsInstants() throws Exception {
		add(talent, timed("2026-12-05T10:00:00+05:30", "2026-12-05T14:00:00+05:30")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("BUSY_BLOCK")).andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.start").value("2026-12-05T04:30:00Z")).andExpect(jsonPath("$.end").value("2026-12-05T08:30:00Z"))
				.andExpect(jsonPath("$.allDay").value(false)).andExpect(jsonPath("$.startDate").doesNotExist());
	}

	@Test
	void anAllDayBlockForFiveDecemberIsReadInTheTalentsCityZone() throws Exception {
		add(talent, allDay("2026-12-05", "2026-12-05")).andExpect(status().isCreated()).andExpect(jsonPath("$.allDay").value(true))
				.andExpect(jsonPath("$.start").value("2026-12-04T18:30:00Z")).andExpect(jsonPath("$.end").value("2026-12-05T18:30:00Z"))
				.andExpect(jsonPath("$.startDate").value("2026-12-05")).andExpect(jsonPath("$.endDate").value("2026-12-05"));
	}

	@Test
	void aMultiDayBlockKeepsItsDatesForDisplay() throws Exception {
		add(talent, allDay("2026-12-05", "2026-12-07")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.start").value("2026-12-04T18:30:00Z")).andExpect(jsonPath("$.end").value("2026-12-07T18:30:00Z"))
				.andExpect(jsonPath("$.startDate").value("2026-12-05")).andExpect(jsonPath("$.endDate").value("2026-12-07"));
	}

	@Test
	void aTalentWithAProfileButNoLocationUsesTheDefaultZone() throws Exception {
		TestAccount noLocation = accounts.create();
		profile.put(noLocation, "work", profile.simpleWork(profile.categoryId("photographer"))).andExpect(status().isOk());

		add(noLocation, allDay("2026-12-05", "2026-12-05")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.start").value("2026-12-04T18:30:00Z")); // Asia/Kolkata, the configured default
	}

	@Test
	void aTalentWithoutAnyProfileGetsA409() throws Exception {
		add(accounts.create(), timed("2026-12-05T10:00:00Z", "2026-12-05T11:00:00Z")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Set up your Talent profile before adding busy blocks."));
	}

	@Test
	void aHiddenProfileStillCounts() throws Exception {
		profile.put(talent, "visibility", "{\"hidden\":true}").andExpect(status().isOk());

		add(talent, timed("2026-12-05T10:00:00Z", "2026-12-05T11:00:00Z")).andExpect(status().isCreated());
	}

	@Test
	void blocksMayOverlapEachOther() throws Exception {
		add(talent, timed("2026-12-05T10:00:00Z", "2026-12-05T14:00:00Z")).andExpect(status().isCreated());
		add(talent, timed("2026-12-05T12:00:00Z", "2026-12-05T16:00:00Z")).andExpect(status().isCreated());
		add(talent, allDay("2026-12-05", "2026-12-05")).andExpect(status().isCreated());
		add(talent, timed("2026-12-05T10:00:00Z", "2026-12-05T14:00:00Z")).andExpect(status().isCreated()); // an exact repeat
	}

	// ---- rules -----------------------------------------------------------------------------------------------------------

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"start\":\"2026-12-05T10:00:00Z\",\"end\":\"2026-12-05T10:00:00Z\"}", // empty
			"{\"start\":\"2026-12-05T10:00:00Z\",\"end\":\"2026-12-05T09:00:00Z\"}", // end before start
			"{\"start\":\"2026-11-10T10:00:00Z\",\"end\":\"2026-11-11T09:00:00Z\"}", // entirely past
			"{\"start\":\"2026-11-14T05:00:00Z\",\"end\":\"2026-11-14T06:30:00Z\"}", // ends exactly now
			"{\"start\":\"2027-11-14T06:31:00Z\",\"end\":\"2027-11-15T06:31:00Z\"}", // starts more than a year ahead
			"{\"start\":\"2026-12-01T00:00:00Z\",\"end\":\"2027-03-01T00:00:01Z\"}", // longer than 90 days
			"{\"start\":\"2026-12-05T10:00:00Z\"}", // end missing
			"{\"end\":\"2026-12-05T10:00:00Z\"}", // start missing
			"{}", // neither form
			"{\"start\":\"2026-12-05T10:00:00Z\",\"end\":\"2026-12-05T11:00:00Z\",\"allDay\":true,\"startDate\":\"2026-12-05\",\"endDate\":\"2026-12-05\"}", // both forms
			"{\"start\":\"2026-12-05T10:00:00Z\",\"end\":\"2026-12-05T11:00:00Z\",\"allDay\":true}", // timed with allDay
			"{\"allDay\":true}", // all-day without dates
			"{\"allDay\":true,\"startDate\":\"2026-12-05\"}", // end date missing
			"{\"allDay\":false,\"startDate\":\"2026-12-05\",\"endDate\":\"2026-12-05\"}", // dates need allDay
			"{\"startDate\":\"2026-12-05\",\"endDate\":\"2026-12-05\"}", // dates without allDay
			"{\"allDay\":true,\"startDate\":\"2026-12-06\",\"endDate\":\"2026-12-05\"}", // last before first
			"{\"allDay\":true,\"startDate\":\"2026-11-13\",\"endDate\":\"2026-11-13\"}", // yesterday
			"{\"allDay\":true,\"startDate\":\"2026-12-01\",\"endDate\":\"2027-03-01\"}", // 91 days
			"{\"start\":\"not a time\",\"end\":\"2026-12-05T11:00:00Z\"}", "{\"start\":\"2026-12-05T10:00:00\",\"end\":\"2026-12-05T11:00:00\"}", // no offset
			"not json" })
	void rulesAreEnforcedWith400(String body) throws Exception {
		add(talent, body).andExpect(status().isBadRequest());

		assertThat(jdbc.sql("SELECT count(*) FROM availability.busy_block WHERE account_id = :a").param("a", talent.id()).query(Integer.class).single()).isZero();
	}

	@Test
	void theBoundariesOfTheRulesAreAccepted() throws Exception {
		add(talent, allDay("2026-11-14", "2026-11-14")).andExpect(status().isCreated()); // today
		add(talent, timed("2026-11-13T10:00:00Z", "2026-11-14T06:30:00.000001Z")).andExpect(status().isCreated()); // still running
		add(talent, timed("2027-11-14T06:30:00Z", "2027-11-15T06:30:00Z")).andExpect(status().isCreated()); // starts exactly a year ahead
		add(talent, timed("2026-12-01T00:00:00Z", "2027-03-01T00:00:00Z")).andExpect(status().isCreated()); // exactly 90 days
		add(talent, allDay("2026-12-01", "2027-02-28")).andExpect(status().isCreated()); // 90 days
	}

	@Test
	void aTokenIsRequired() throws Exception {
		add(null, timed("2026-12-05T10:00:00Z", "2026-12-05T11:00:00Z")).andExpect(status().isUnauthorized());
		mvc.perform(delete("/v1/me/busy-blocks/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
		mvc.perform(get("/v1/me/calendar").param("from", "2026-12-01").param("to", "2026-12-31")).andExpect(status().isUnauthorized());
	}

	// ---- removing ------------------------------------------------------------------------------------------------------------

	@Test
	void aTalentRemovesTheirOwnBlock() throws Exception {
		String id = idOf(add(talent, timed("2026-12-05T10:00:00Z", "2026-12-05T11:00:00Z")));

		mvc.perform(delete("/v1/me/busy-blocks/" + id).header("Authorization", talent.header())).andExpect(status().isNoContent());

		calendar(talent, "2026-12-01", "2026-12-31").andExpect(jsonPath("$.entries", hasSize(0)));
		mvc.perform(delete("/v1/me/busy-blocks/" + id).header("Authorization", talent.header())).andExpect(status().isNotFound()); // already gone
	}

	@Test
	void someoneElsesOrAnUnknownBlockIsNotFoundAndStaysUntouched() throws Exception {
		TestAccount other = withLocation(accounts.create());
		String id = idOf(add(talent, timed("2026-12-05T10:00:00Z", "2026-12-05T11:00:00Z")));

		mvc.perform(delete("/v1/me/busy-blocks/" + id).header("Authorization", other.header())).andExpect(status().isNotFound());
		mvc.perform(delete("/v1/me/busy-blocks/" + UUID.randomUUID()).header("Authorization", other.header())).andExpect(status().isNotFound());
		mvc.perform(delete("/v1/me/busy-blocks/not-a-uuid").header("Authorization", other.header())).andExpect(status().isBadRequest());

		calendar(talent, "2026-12-01", "2026-12-31").andExpect(jsonPath("$.entries", hasSize(1)));
	}

	// ---- calendar --------------------------------------------------------------------------------------------------------------

	@Test
	void theCalendarListsBlocksAndActiveBookingsSortedByStart() throws Exception {
		UUID gig = UUID.randomUUID();
		add(talent, timed("2026-12-10T10:00:00Z", "2026-12-10T11:00:00Z"));
		availability.book(talent.id(), gig, Instant.parse("2026-12-05T08:00:00Z"), Instant.parse("2026-12-05T12:00:00Z"));
		add(talent, allDay("2026-12-07", "2026-12-08"));
		UUID cancelled = UUID.randomUUID();
		availability.book(talent.id(), cancelled, Instant.parse("2026-12-06T08:00:00Z"), Instant.parse("2026-12-06T12:00:00Z"));
		availability.release(cancelled);

		calendar(talent, "2026-12-01", "2026-12-31").andExpect(status().isOk()).andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"))
				.andExpect(jsonPath("$.from").value("2026-12-01")).andExpect(jsonPath("$.to").value("2026-12-31"))
				.andExpect(jsonPath("$.entries[*].type", contains("BOOKING", "BUSY_BLOCK", "BUSY_BLOCK")))
				.andExpect(jsonPath("$.entries[0].gigId").value(gig.toString())).andExpect(jsonPath("$.entries[0].start").value("2026-12-05T08:00:00Z"))
				.andExpect(jsonPath("$.entries[1].allDay").value(true)).andExpect(jsonPath("$.entries[1].startDate").value("2026-12-07"))
				.andExpect(jsonPath("$.entries[1].endDate").value("2026-12-08")).andExpect(jsonPath("$.entries[2].start").value("2026-12-10T10:00:00Z"));
	}

	@Test
	void theRangeIsReadInTheTalentsZoneAndIsHalfOpen() throws Exception {
		add(talent, allDay("2026-12-05", "2026-12-05")); // [4 Dec 18:30Z, 5 Dec 18:30Z)

		calendar(talent, "2026-12-05", "2026-12-05").andExpect(jsonPath("$.entries", hasSize(1)));
		calendar(talent, "2026-12-04", "2026-12-04").andExpect(jsonPath("$.entries", hasSize(0))); // the day before, in Kolkata
		calendar(talent, "2026-12-06", "2026-12-06").andExpect(jsonPath("$.entries", hasSize(0)));
		add(talent, timed("2026-12-10T00:00:00+05:30", "2026-12-11T00:00:00+05:30")); // ends exactly at the end of 10 Dec
		calendar(talent, "2026-12-11", "2026-12-11").andExpect(jsonPath("$.entries", hasSize(0)));
		calendar(talent, "2026-12-10", "2026-12-10").andExpect(jsonPath("$.entries", hasSize(1)));
	}

	@Test
	void aBlockOverlappingTheRangeOnlyPartlyIsIncluded() throws Exception {
		add(talent, timed("2026-11-30T20:00:00Z", "2026-12-01T20:00:00Z"));

		calendar(talent, "2026-12-01", "2026-12-02").andExpect(jsonPath("$.entries", hasSize(1)));
	}

	@Test
	void theCalendarIsPrivate() throws Exception {
		TestAccount other = withLocation(accounts.create());
		add(talent, timed("2026-12-05T10:00:00Z", "2026-12-05T11:00:00Z"));
		availability.book(talent.id(), UUID.randomUUID(), Instant.parse("2026-12-06T10:00:00Z"), Instant.parse("2026-12-06T11:00:00Z"));

		calendar(other, "2026-12-01", "2026-12-31").andExpect(jsonPath("$.entries", hasSize(0)));
		String publicView = mvc.perform(get("/v1/talent/" + talent.id())).andReturn().getResponse().getContentAsString();
		assertThat(publicView).doesNotContain("2026-12-05").doesNotContain("2026-12-06").doesNotContain("busy");
	}

	@Test
	void aTalentWithoutAProfileSeesAnEmptyCalendarInTheDefaultZone() throws Exception {
		calendar(accounts.create(), "2026-12-01", "2026-12-31").andExpect(status().isOk()).andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"))
				.andExpect(jsonPath("$.entries", hasSize(0)));
	}

	@Test
	void theCalendarRangeIsLimitedAndMustBeValid() throws Exception {
		calendar(talent, "2026-12-01", "2027-03-02").andExpect(status().isOk()); // exactly 92 days
		calendar(talent, "2026-12-01", "2027-03-03").andExpect(status().isBadRequest()); // 93 days
		calendar(talent, "2026-12-05", "2026-12-04").andExpect(status().isBadRequest());
		calendar(talent, "2026-12-05", "2026-12-05").andExpect(status().isOk());
		mvc.perform(get("/v1/me/calendar").param("from", "2026-12-01").header("Authorization", talent.header())).andExpect(status().isBadRequest());
		mvc.perform(get("/v1/me/calendar").header("Authorization", talent.header())).andExpect(status().isBadRequest());
		calendar(talent, "01-12-2026", "2026-12-31").andExpect(status().isBadRequest());
		calendar(talent, "yesterday", "today").andExpect(status().isBadRequest());
	}

	private static String idOf(ResultActions created) throws Exception {
		String body = created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(body, "$.id");
	}

}
