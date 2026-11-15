package com.gigwork.core.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/** Shortlisting, removing, sharing and consenting (FR-25, FR-25a, FR-26, NFR-38). */
@IdentityIntegrationTest
@Import(ShortlistApiTest.Events.class)
class ShortlistApiTest {

	@org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
	static class Events {

		@org.springframework.context.annotation.Bean
		ShortlistEvents shortlistEvents() {
			return new ShortlistEvents();
		}

	}

	/** Collects the shortlist events. */
	static class ShortlistEvents {

		final java.util.List<ApplicantShortlisted> all = new java.util.concurrent.CopyOnWriteArrayList<>();

		@org.springframework.context.event.EventListener
		void on(ApplicantShortlisted e) {
			all.add(e);
		}

	}

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
	@Autowired
	ShortlistEvents events;

	HiringApiSupport api;
	TestAccount poster;
	TestAccount talent;
	UUID gig;
	UUID application;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		events.all.clear();
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		poster = accounts.create();
		talent = api.talent("Asha", "photographer");
		gig = api.openGig(poster);
		application = api.applyOk(talent, gig);
	}

	private int actions(TestAccount account) {
		return jdbc.sql("SELECT count(*) FROM hiring.shortlist_action WHERE poster_id = :p").param("p", account.id())
				.query(Integer.class).single();
	}

	private String entry(String column) {
		Object value = jdbc.sql("SELECT " + column + " AS v FROM hiring.shortlist_entry WHERE application_id = :a").param("a", application)
				.query().singleRow().get("v");
		return value == null ? null : value.toString();
	}

	// ---- shortlisting ------------------------------------------------------------------------------------------------------

	@Test
	void aPosterShortlistsAnApplicantWithoutSharingAndTheEventIsPublished() throws Exception {
		api.shortlist(poster, application, false).andExpect(status().isOk()).andExpect(jsonPath("$.shortlisted").value(true))
				.andExpect(jsonPath("$.posterShares").value(false)).andExpect(jsonPath("$.talentConsented").value(false));

		assertThat(entry("removed_at")).isNull();
		assertThat(actions(poster)).isEqualTo(1);
		assertThat(events.all).singleElement().satisfies(e -> {
			assertThat(e.applicationId()).isEqualTo(application);
			assertThat(e.gigId()).isEqualTo(gig);
			assertThat(e.posterId()).isEqualTo(poster.id());
			assertThat(e.talentId()).isEqualTo(talent.id());
			assertThat(e.posterSharesContact()).isFalse();
		});
	}

	@Test
	void sharingTheOwnContactNeedsAContactMethod() throws Exception {
		api.shortlist(poster, application, true).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_METHOD_REQUIRED"));
		assertThat(actions(poster)).isZero();
		assertThat(events.all).isEmpty();

		api.sharesContact(poster);
		api.shortlist(poster, application, true).andExpect(status().isOk()).andExpect(jsonPath("$.posterShares").value(true));
	}

	@Test
	void onlyTheGigsPosterCanShortlistAndEveryoneElseGetsA404() throws Exception {
		api.shortlist(accounts.create(), application, false).andExpect(status().isNotFound());
		api.shortlist(talent, application, false).andExpect(status().isNotFound()); // not even the applicant
		api.shortlist(poster, UUID.randomUUID(), false).andExpect(status().isNotFound());
		api.shortlist(null, application, false).andExpect(status().isUnauthorized());
		assertThat(actions(poster)).isZero();
	}

	@Test
	void aWithdrawnApplicationOrAClosedGigCannotBeShortlisted() throws Exception {
		TestAccount other = api.talent("Bela", "photographer");
		UUID withdrawn = api.applyOk(other, gig);
		api.withdraw(other, withdrawn).andExpect(status().isNoContent());
		api.shortlist(poster, withdrawn, false).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPLICATION_NOT_ACTIVE"));

		jdbc.sql("UPDATE gigs.gig SET status = 'CANCELLED', closed_at = now() WHERE id = :id").param("id", gig).update();
		api.shortlist(poster, application, false).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GIG_NOT_OPEN"));
		assertThat(actions(poster)).isZero();
	}

	@Test
	void shortlistingTwiceIsRefusedAndTheSecondTimeDoesNotCount() throws Exception {
		api.shortlist(poster, application, false).andExpect(status().isOk());
		api.shortlist(poster, application, false).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_SHORTLISTED"));
		assertThat(actions(poster)).isEqualTo(1);
		assertThat(events.all).hasSize(1);
	}

	@Test
	void aPausedProfileCanBeShortlisted() throws Exception {
		api.profiles.put(talent, "visibility", "{\"hidden\":true}").andExpect(status().isOk());
		api.shortlist(poster, application, false).andExpect(status().isOk());
	}

	@Test
	void theBodyMustSayWhetherToShare() throws Exception {
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/applications/" + application + "/shortlist")
				.header("Authorization", poster.header()).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	// ---- the limits ----------------------------------------------------------------------------------------------------------

	@Test
	void theEleventhShortlistedTalentOfAGigIsRefusedAndRolledBack() throws Exception {
		for (int i = 0; i < 9; i++) {
			UUID app = api.applyOk(api.talent("T" + i, "photographer"), gig);
			api.shortlist(poster, app, false).andExpect(status().isOk());
		}
		api.shortlist(poster, application, false).andExpect(status().isOk()); // the tenth
		UUID eleventh = api.applyOk(api.talent("Late", "photographer"), gig);

		api.shortlist(poster, eleventh, false).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHORTLIST_FULL"));

		assertThat(jdbc.sql("SELECT count(*) FROM hiring.shortlist_entry WHERE gig_id = :g AND removed_at IS NULL").param("g", gig)
				.query(Integer.class).single()).isEqualTo(10);
		assertThat(actions(poster)).isEqualTo(10);
		// removing one frees a place
		api.removeFromShortlist(poster, application).andExpect(status().isNoContent());
		api.shortlist(poster, eleventh, false).andExpect(status().isOk());
	}

	@Test
	void theTwentyFirstShortlistActionInADayIsRefusedButRemovingIsStillAllowed() throws Exception {
		// 10 add/remove pairs: 20 actions
		for (int i = 0; i < 10; i++) {
			api.shortlist(poster, application, false).andExpect(status().isOk());
			api.removeFromShortlist(poster, application).andExpect(status().isNoContent());
		}
		assertThat(actions(poster)).isEqualTo(20);

		api.shortlist(poster, application, false).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("SHORTLIST_ACTION_LIMIT"));
		assertThat(actions(poster)).isEqualTo(20);
		assertThat(jdbc.sql("SELECT count(*) FROM hiring.shortlist_entry WHERE application_id = :a AND removed_at IS NULL").param("a", application)
				.query(Integer.class).single()).isZero(); // the refused add left nothing behind

		clock.advance(Duration.ofHours(24).plusMinutes(1)); // the window rolls
		api.shortlist(poster(), application, false).andExpect(status().isOk());
	}

	private TestAccount poster() {
		return new TestAccount(poster.id(), accounts.token(poster.id()));
	}

	@Test
	void removingAtTheLimitStillWorks() throws Exception {
		for (int i = 0; i < 10; i++) {
			api.shortlist(poster, application, false).andExpect(status().isOk());
			if (i < 9) {
				api.removeFromShortlist(poster, application).andExpect(status().isNoContent());
			}
		}
		assertThat(actions(poster)).isEqualTo(19);
		api.removeFromShortlist(poster, application).andExpect(status().isNoContent()); // the 20th
		api.removeFromShortlist(poster, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_SHORTLISTED"));
	}

	// ---- removing ------------------------------------------------------------------------------------------------------------

	@Test
	void removingEndsTheShortlistWithoutAnyEventAndOnlyThePosterCanRemove() throws Exception {
		api.shortlist(poster, application, false).andExpect(status().isOk());
		events.all.clear();

		api.removeFromShortlist(talent, application).andExpect(status().isNotFound());
		api.removeFromShortlist(poster, application).andExpect(status().isNoContent());

		assertThat(entry("removed_at")).isNotNull();
		assertThat(events.all).isEmpty();
		assertThat(actions(poster)).isEqualTo(2);
		api.removeFromShortlist(poster, application).andExpect(status().isConflict());
	}

	@Test
	void removingSomeoneNeverShortlistedIsRefused() throws Exception {
		api.removeFromShortlist(poster, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_SHORTLISTED"));
		assertThat(actions(poster)).isZero();
	}

	// ---- the Poster's share and the Talent's consent --------------------------------------------------------------------

	@Test
	void thePosterChangesTheirShareChoiceAtAnyTimeWhileShortlisted() throws Exception {
		api.posterShare(poster, application, false).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_SHORTLISTED"));
		api.shortlist(poster, application, false).andExpect(status().isOk());

		api.posterShare(poster, application, true).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_METHOD_REQUIRED"));
		api.sharesContact(poster);
		api.posterShare(poster, application, true).andExpect(status().isOk()).andExpect(jsonPath("$.posterShares").value(true));
		api.posterShare(poster, application, false).andExpect(status().isOk()).andExpect(jsonPath("$.posterShares").value(false));
		api.posterShare(talent, application, true).andExpect(status().isNotFound());
		assertThat(actions(poster)).isEqualTo(1); // changing the choice is not a shortlist action
	}

	@Test
	void theTalentConsentsAndWithdrawsOnlyWhileShortlistedAndWithAContactMethod() throws Exception {
		api.consent(talent, application, true).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_METHOD_REQUIRED"));
		api.sharesContact(talent);
		api.consent(talent, application, true).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_SHORTLISTED"));
		api.shortlist(poster, application, false).andExpect(status().isOk());
		api.consent(talent, application, true).andExpect(status().isOk()).andExpect(jsonPath("$.consented").value(true));
		String consentedAt = entry("talent_consented_at");
		api.consent(talent, application, true).andExpect(status().isOk()); // idempotent: the time doesn't move
		assertThat(entry("talent_consented_at")).isEqualTo(consentedAt);
		api.consent(talent, application, false).andExpect(status().isOk()).andExpect(jsonPath("$.consented").value(false));
		assertThat(entry("consent_withdrawn_at")).isNotNull();
		api.consent(talent, application, false).andExpect(status().isOk()); // idempotent
		api.consent(talent, application, true).andExpect(status().isOk()).andExpect(jsonPath("$.consented").value(true)); // can consent again
		assertThat(entry("consent_withdrawn_at")).isNull();
	}

	@Test
	void onlyTheApplicantCanConsent() throws Exception {
		api.shortlist(poster, application, false).andExpect(status().isOk());
		api.sharesContact(talent);
		api.consent(poster, application, true).andExpect(status().isNotFound());
		api.consent(accounts.create(), application, true).andExpect(status().isNotFound());
		api.consent(talent, UUID.randomUUID(), true).andExpect(status().isNotFound());
		api.consent(null, application, true).andExpect(status().isUnauthorized());
	}

	@Test
	void addingAgainAfterRemovalResetsTheConsent() throws Exception {
		api.sharesContact(talent);
		api.shortlist(poster, application, false).andExpect(status().isOk());
		api.consent(talent, application, true).andExpect(status().isOk());
		api.removeFromShortlist(poster, application).andExpect(status().isNoContent());

		api.shortlist(poster, application, false).andExpect(status().isOk()).andExpect(jsonPath("$.talentConsented").value(false));

		assertThat(entry("talent_consented_at")).isNull();
		assertThat(entry("consent_withdrawn_at")).isNull();
		assertThat(entry("removed_at")).isNull();
		assertThat(jdbc.sql("SELECT count(*) FROM hiring.shortlist_entry WHERE application_id = :a").param("a", application)
				.query(Integer.class).single()).isEqualTo(1); // the same row
		assertThat(events.all).hasSize(2); // the Talent is notified of each shortlisting
	}

	@Test
	void withdrawingTheApplicationEndsTheShortlistAndAFreshApplicationCanBeShortlistedAgain() throws Exception {
		api.shortlist(poster, application, false).andExpect(status().isOk());

		api.withdraw(talent, application).andExpect(status().isNoContent());

		assertThat(entry("removed_at")).isNotNull();
		assertThat(actions(poster)).isEqualTo(1); // withdrawing is not a Poster action
		api.shortlist(poster, application, false).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPLICATION_NOT_ACTIVE"));
		api.applyOk(talent, gig); // applies again: the same application becomes active
		api.shortlist(poster, application, false).andExpect(status().isOk());
	}

}
