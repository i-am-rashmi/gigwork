package com.gigwork.core.gigs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import com.gigwork.core.reference.ReferenceData;

/** Editing (FR-18) and cancelling (FR-19) an open gig: the Poster only, and only while it is open and has not started. */
@IdentityIntegrationTest
@RecordApplicationEvents
class GigEditCancelApiTest {

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
	TestAccount other;

	@BeforeEach
	void setUp() {
		clock.reset();
		api = new GigApiSupport(mvc, reference, clock);
		poster = accounts.create();
		other = accounts.create();
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	private Map<String, Object> row(UUID id) {
		return jdbc.sql("SELECT * FROM gigs.gig WHERE id = :id").param("id", id).query().singleRow();
	}

	private static java.time.Instant instant(Object timestamp) {
		return timestamp instanceof java.time.OffsetDateTime o ? o.toInstant() : ((java.sql.Timestamp) timestamp).toInstant();
	}

	private UUID published(Gig gig) throws Exception {
		UUID id = api.publishOk(poster, gig);
		events.clear();
		return id;
	}

	private String statusOf(UUID id) {
		return (String) row(id).get("status");
	}

	// ---- editing -------------------------------------------------------------------------------------------------

	@Test
	void anEditReplacesEveryEditableFieldAndClearsTheOnesLeftOut() throws Exception {
		UUID id = published(api.onSite().specialisation(api.specialisation("photographer", "wedding")).budget("1000", "2000"));
		Gig changed = api.onSite().title("Candid photographer, one evening").description("Reception only.")
				.startsIn(Duration.ofDays(10)).lasting(Duration.ofHours(4)).ta(false).budget("3000", "3000");
		clock.advance(Duration.ofMinutes(1));

		api.edit(poster, id, changed).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Candid photographer, one evening"))
				.andExpect(jsonPath("$.taOffered").value(false)).andExpect(jsonPath("$.budgetMin").value(3000))
				.andExpect(jsonPath("$.specialisationId").doesNotExist()).andExpect(jsonPath("$.status").value("OPEN"));

		Map<String, Object> stored = row(id);
		assertThat(stored.get("title")).isEqualTo("Candid photographer, one evening");
		assertThat(stored.get("specialisation_id")).isNull();
		assertThat(instant(stored.get("start_at"))).isEqualTo(changed.start());
		assertThat(stored.get("updated_at")).isNotEqualTo(stored.get("published_at"));
	}

	@Test
	void anEditThatChangesTheDateWritesOneGigChangedEventSayingSo() throws Exception {
		UUID id = published(api.onSite());

		api.edit(poster, id, api.onSite().startsIn(Duration.ofDays(5))).andExpect(status().isOk());

		var changed = events.stream(GigChanged.class).toList();
		assertThat(changed).hasSize(1);
		assertThat(changed.get(0).dateTimeChanged()).isTrue();
		assertThat(changed.get(0).locationChanged()).isFalse();
		assertThat(changed.get(0).matchingChanged()).isFalse();
		assertThat(changed.get(0).gigId()).isEqualTo(id);
		assertThat(changed.get(0).posterId()).isEqualTo(poster.id());
	}

	@Test
	void anEditThatMovesTheLocationSaysSo() throws Exception {
		UUID id = published(api.onSite().locality(api.indiranagar()));

		api.edit(poster, id, api.onSite().point(12.9352, 77.6245)).andExpect(status().isOk());

		var changed = events.stream(GigChanged.class).toList();
		assertThat(changed).hasSize(1);
		assertThat(changed.get(0).locationChanged()).isTrue();
		assertThat(changed.get(0).dateTimeChanged()).isFalse();
	}

	@Test
	void anEditThatChangesTheSpecialisationOrLanguageAsksForMatchingAgain() throws Exception {
		UUID id = published(api.onSite().category(api.category("anchor")));

		api.edit(poster, id, api.onSite().category(api.category("anchor")).language("hi")).andExpect(status().isOk());

		var changed = events.stream(GigChanged.class).toList();
		assertThat(changed).hasSize(1);
		assertThat(changed.get(0).matchingChanged()).isTrue();
		assertThat(changed.get(0).dateTimeChanged()).isFalse();
	}

	@Test
	void anEditOfOnlyTheTextOrBudgetWritesNoEvent() throws Exception {
		UUID id = published(api.onSite());

		api.edit(poster, id, api.onSite().title("A new title").budget("100", "200").ta(false)).andExpect(status().isOk());
		api.edit(poster, id, api.onSite().title("A new title").budget("100", "200").ta(false)).andExpect(status().isOk());

		assertThat(events.stream(GigChanged.class).count()).isZero();
	}

	@Test
	void theCategoryAndTheWorkModeCannotBeChanged() throws Exception {
		UUID onSite = published(api.onSite());
		UUID both = published(api.onSite().category(api.category("painter-caricature")));

		api.edit(poster, onSite, api.onSite().category(api.category("videographer"))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("can't be changed")));
		api.edit(poster, both, api.remote().category(api.category("painter-caricature"))).andExpect(status().isBadRequest());

		assertThat(row(onSite).get("category_id")).isEqualTo(api.category("photographer"));
		assertThat(row(both).get("work_mode")).isEqualTo("ON_SITE");
	}

	@Test
	void anEditIsCheckedAgainstTheSameRulesAsPublishingAndChangesNothingWhenRefused() throws Exception {
		UUID id = published(api.onSite());

		api.edit(poster, id, api.onSite().startsIn(Duration.ofHours(-3))).andExpect(status().isBadRequest());
		api.edit(poster, id, api.onSite().title("Call 9876543210")).andExpect(status().isBadRequest());
		api.edit(poster, id, api.onSite().lasting(Duration.ofDays(15))).andExpect(status().isBadRequest());

		assertThat(row(id).get("title")).isEqualTo("Photographer for a two-day wedding");
		assertThat(events.stream(GigChanged.class).count()).isZero();
	}

	@Test
	void onlyThePosterCanEditAndAnotherAccountOrAnUnknownGigIsNotFound() throws Exception {
		UUID id = published(api.onSite());

		api.edit(other, id, api.onSite().title("Hijacked")).andExpect(status().isNotFound());
		api.edit(poster, UUID.randomUUID(), api.onSite()).andExpect(status().isNotFound());
		api.edit(null, id, api.onSite()).andExpect(status().isUnauthorized());

		assertThat(row(id).get("title")).isEqualTo("Photographer for a two-day wedding");
	}

	@Test
	void aCancelledFilledOrExpiredGigCannotBeEdited() throws Exception {
		UUID cancelled = published(api.onSite());
		api.cancel(poster, cancelled, "{}").andExpect(status().isOk());
		UUID filled = published(api.onSite());
		jdbc.sql("UPDATE gigs.gig SET status = 'FILLED' WHERE id = :id").param("id", filled).update();
		UUID expired = published(api.onSite());
		jdbc.sql("UPDATE gigs.gig SET status = 'EXPIRED', closed_at = now() WHERE id = :id").param("id", expired).update();

		for (UUID id : new UUID[] { cancelled, filled, expired }) {
			api.edit(poster, id, api.onSite().title("Too late")).andExpect(status().isConflict())
					.andExpect(jsonPath("$.title").value("Gig not open"));
			assertThat(row(id).get("title")).isEqualTo("Photographer for a two-day wedding");
		}
	}

	@Test
	void aGigThatHasStartedCannotBeEditedEvenIfTheExpiryJobHasNotRunYet() throws Exception {
		UUID id = published(api.onSite());
		Gig later = api.onSite().startsIn(Duration.ofDays(30)).title("Moved");
		clock.advance(Duration.ofDays(4)); // past the start; nothing has expired the gig
		poster = new TestAccount(poster.id(), accounts.token(poster.id())); // the old token has expired by this clock

		assertThat(statusOf(id)).isEqualTo("OPEN");
		api.edit(poster, id, later).andExpect(status().isConflict());
		assertThat(row(id).get("title")).isEqualTo("Photographer for a two-day wedding");
	}

	// ---- cancelling ------------------------------------------------------------------------------------------------

	@Test
	void thePosterCancelsAnOpenGigWithAReasonAndItIsKept() throws Exception {
		UUID id = published(api.onSite());

		api.cancel(poster, id, "{\"reason\":\"  The venue changed  \"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.closeReason").value("The venue changed"))
				.andExpect(jsonPath("$.closedAt").isNotEmpty());

		Map<String, Object> stored = row(id);
		assertThat(stored.get("status")).isEqualTo("CANCELLED");
		assertThat(stored.get("closed_at")).isNotNull();
		assertThat(stored.get("close_reason")).isEqualTo("The venue changed");
		var cancelled = events.stream(GigCancelled.class).toList();
		assertThat(cancelled).hasSize(1);
		assertThat(cancelled.get(0).gigId()).isEqualTo(id);
		assertThat(cancelled.get(0).posterId()).isEqualTo(poster.id());
	}

	@Test
	void theReasonIsOptionalAndABlankOneIsNotStored() throws Exception {
		UUID withoutBody = published(api.onSite());
		UUID blank = published(api.onSite());

		api.cancel(poster, withoutBody, "").andExpect(status().isOk());
		api.cancel(poster, blank, "{\"reason\":\"   \"}").andExpect(status().isOk());

		assertThat(row(withoutBody).get("close_reason")).isNull();
		assertThat(row(blank).get("close_reason")).isNull();
	}

	@Test
	void aReasonOverFiveHundredCharactersIsRefusedAndTheGigStaysOpen() throws Exception {
		UUID id = published(api.onSite());

		api.cancel(poster, id, "{\"reason\":\"" + "r".repeat(501) + "\"}").andExpect(status().isBadRequest());

		assertThat(statusOf(id)).isEqualTo("OPEN");
	}

	@Test
	void aGigCannotBeCancelledTwiceOrAfterItClosedAnotherWay() throws Exception {
		UUID id = published(api.onSite());
		UUID expired = published(api.onSite());
		jdbc.sql("UPDATE gigs.gig SET status = 'EXPIRED', closed_at = now() WHERE id = :id").param("id", expired).update();
		UUID filled = published(api.onSite());
		jdbc.sql("UPDATE gigs.gig SET status = 'FILLED' WHERE id = :id").param("id", filled).update();

		api.cancel(poster, id, "{}").andExpect(status().isOk());
		events.clear();

		api.cancel(poster, id, "{}").andExpect(status().isConflict());
		api.cancel(poster, expired, "{}").andExpect(status().isConflict());
		api.cancel(poster, filled, "{}").andExpect(status().isConflict()); // cancelling a filled gig comes with hiring
		assertThat(statusOf(expired)).isEqualTo("EXPIRED");
		assertThat(statusOf(filled)).isEqualTo("FILLED");
		assertThat(events.stream(GigCancelled.class).count()).isZero();
	}

	@Test
	void onlyThePosterCanCancelAndAnotherAccountOrAnUnknownGigIsNotFound() throws Exception {
		UUID id = published(api.onSite());

		api.cancel(other, id, "{}").andExpect(status().isNotFound());
		api.cancel(poster, UUID.randomUUID(), "{}").andExpect(status().isNotFound());
		api.cancel(null, id, "{}").andExpect(status().isUnauthorized());

		assertThat(statusOf(id)).isEqualTo("OPEN");
	}

	@Test
	void aGigThatHasStartedButIsStillOpenCanBeCancelled() throws Exception {
		UUID id = published(api.onSite());
		clock.advance(Duration.ofDays(4));
		poster = new TestAccount(poster.id(), accounts.token(poster.id()));

		api.cancel(poster, id, "{}").andExpect(status().isOk());

		assertThat(statusOf(id)).isEqualTo("CANCELLED");
	}

}
