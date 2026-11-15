package com.gigwork.core.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/** Viewing the other side's contact (FR-26): the conditions, the log, and the moment access ends. */
@IdentityIntegrationTest
class ContactViewApiTest {

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

	/** Both sides share and the Talent is shortlisted and has consented. */
	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		poster = accounts.create();
		talent = api.talent("Asha", "photographer");
		api.sharesContact(poster);
		api.sharesContact(talent);
		gig = api.openGig(poster);
		application = api.applyOk(talent, gig);
		api.shortlist(poster, application, true).andExpect(status().isOk());
		api.consent(talent, application, true).andExpect(status().isOk());
	}

	private int logRows() {
		return jdbc.sql("SELECT count(*) FROM hiring.contact_access_log WHERE application_id = :a").param("a", application)
				.query(Integer.class).single();
	}

	private void bothViewsAreRefused() throws Exception {
		int before = logRows();
		api.talentContact(poster, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));
		api.posterContact(talent, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));
		assertThat(logRows()).isEqualTo(before);
	}

	// ---- a successful view ---------------------------------------------------------------------------------------------------

	@Test
	void thePosterSeesTheTalentsContactAndTheViewIsLoggedOnce() throws Exception {
		api.talentContact(poster, application).andExpect(status().isOk()).andExpect(jsonPath("$.methods[0]").value("WHATSAPP"))
				.andExpect(jsonPath("$.methods[1]").value("CALL")).andExpect(jsonPath("$.mobile").value(accounts.mobileOf(talent.id())))
				.andExpect(jsonPath("$.email").doesNotExist()).andExpect(header().string("Cache-Control", containsString("no-store")));

		var log = jdbc.sql("SELECT viewer_id, viewed_id, gig_id, application_id FROM hiring.contact_access_log WHERE application_id = :a")
				.param("a", application).query().singleRow();
		assertThat(log.get("viewer_id")).isEqualTo(poster.id());
		assertThat(log.get("viewed_id")).isEqualTo(talent.id());
		assertThat(log.get("gig_id")).isEqualTo(gig);
	}

	@Test
	void theTalentSeesThePostersContactAndTheViewIsLoggedOnce() throws Exception {
		api.posterContact(talent, application).andExpect(status().isOk()).andExpect(jsonPath("$.mobile").value(accounts.mobileOf(poster.id())))
				.andExpect(header().string("Cache-Control", containsString("no-store")));

		var log = jdbc.sql("SELECT viewer_id, viewed_id FROM hiring.contact_access_log WHERE application_id = :a").param("a", application)
				.query().singleRow();
		assertThat(log.get("viewer_id")).isEqualTo(talent.id());
		assertThat(log.get("viewed_id")).isEqualTo(poster.id());
	}

	@Test
	void everyViewIsLoggedAndOnlyTheChosenMethodsAreShown() throws Exception {
		api.contactMethods(talent, "{\"whatsapp\":false,\"call\":true,\"email\":false}").andExpect(status().isOk());

		api.talentContact(poster, application).andExpect(status().isOk()).andExpect(jsonPath("$.methods.length()").value(1))
				.andExpect(jsonPath("$.methods[0]").value("CALL"));
		api.talentContact(poster, application).andExpect(status().isOk());

		assertThat(logRows()).isEqualTo(2);
	}

	// ---- each condition ------------------------------------------------------------------------------------------------------

	@Test
	void noViewBeforeTheTalentIsShortlistedOrHasConsented() throws Exception {
		TestAccount other = api.talent("Bela", "photographer");
		api.sharesContact(other);
		UUID notShortlisted = api.applyOk(other, gig);
		api.talentContact(poster, notShortlisted).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));
		api.posterContact(other, notShortlisted).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));

		api.shortlist(poster, notShortlisted, true).andExpect(status().isOk()); // shortlisted, not yet consented
		api.talentContact(poster, notShortlisted).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));
		api.posterContact(other, notShortlisted).andExpect(status().isOk()); // the Poster shares, so the Talent can see
		assertThat(jdbc.sql("SELECT count(*) FROM hiring.contact_access_log WHERE application_id = :a").param("a", notShortlisted)
				.query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void aPosterWhoDoesNotShareShowsNothingToTheTalentButCanStillSeeTheTalent() throws Exception {
		api.posterShare(poster, application, false).andExpect(status().isOk());

		api.posterContact(talent, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));
		api.talentContact(poster, application).andExpect(status().isOk());
	}

	// ---- access ends immediately -----------------------------------------------------------------------------------------

	@Test
	void removalEndsAccessForBothAtOnce() throws Exception {
		api.talentContact(poster, application).andExpect(status().isOk());
		api.posterContact(talent, application).andExpect(status().isOk());

		api.removeFromShortlist(poster, application).andExpect(status().isNoContent());

		bothViewsAreRefused();
	}

	@Test
	void withdrawingTheApplicationEndsAccessForBothAtOnce() throws Exception {
		api.talentContact(poster, application).andExpect(status().isOk());

		api.withdraw(talent, application).andExpect(status().isNoContent());

		bothViewsAreRefused();
	}

	@Test
	void withdrawingConsentEndsThePostersAccessAtOnceButNotTheTalents() throws Exception {
		api.talentContact(poster, application).andExpect(status().isOk());

		api.consent(talent, application, false).andExpect(status().isOk());

		api.talentContact(poster, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));
		api.posterContact(talent, application).andExpect(status().isOk()); // the Poster still shares
	}

	@Test
	void thePosterNoLongerSharingEndsTheTalentsAccessAtOnce() throws Exception {
		api.posterContact(talent, application).andExpect(status().isOk());

		api.posterShare(poster, application, false).andExpect(status().isOk());

		api.posterContact(talent, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));
	}

	@Test
	void aGigThatClosesEndsAccessForBoth() throws Exception {
		for (String status : new String[] { "CANCELLED", "EXPIRED", "FILLED" }) {
			jdbc.sql("UPDATE gigs.gig SET status = :s, closed_at = CASE WHEN :s = 'FILLED' THEN NULL ELSE now() END WHERE id = :id")
					.param("s", status).param("id", gig).update();
			bothViewsAreRefused();
			jdbc.sql("UPDATE gigs.gig SET status = 'OPEN', closed_at = NULL WHERE id = :id").param("id", gig).update();
			api.talentContact(poster, application).andExpect(status().isOk()); // open again: access is back (the entry never changed)
		}
	}

	@Test
	void addingTheTalentAgainAsksForConsentAgain() throws Exception {
		api.removeFromShortlist(poster, application).andExpect(status().isNoContent());
		api.shortlist(poster, application, true).andExpect(status().isOk());

		api.talentContact(poster, application).andExpect(status().isConflict()); // consent did not carry over
		api.posterContact(talent, application).andExpect(status().isOk()); // the Poster shares again
		api.consent(talent, application, true).andExpect(status().isOk());
		api.talentContact(poster, application).andExpect(status().isOk());
	}

	@Test
	void ifTheViewedPersonHasNoContactMethodLeftTheViewIsRefusedAndNotLogged() throws Exception {
		api.contactMethods(talent, "{\"whatsapp\":false,\"call\":false,\"email\":false}").andExpect(status().isOk());
		int before = logRows();

		api.talentContact(poster, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));
		assertThat(logRows()).isEqualTo(before);

		api.contactMethods(poster, "{\"whatsapp\":false,\"call\":false,\"email\":false}").andExpect(status().isOk());
		api.posterContact(talent, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));
		assertThat(logRows()).isEqualTo(before);
	}

	// ---- if logging fails, nothing is revealed ---------------------------------------------------------------------------

	@Test
	void ifTheViewCannotBeLoggedNothingIsReturnedAndNoRowRemains() {
		jdbc.sql("""
				CREATE FUNCTION hiring.test_refuse_log() RETURNS trigger LANGUAGE plpgsql AS
				$$ BEGIN RAISE EXCEPTION 'simulated logging failure'; END; $$""").update();
		jdbc.sql("CREATE TRIGGER test_refuse_log BEFORE INSERT ON hiring.contact_access_log FOR EACH ROW EXECUTE FUNCTION hiring.test_refuse_log()")
				.update();
		try {
			String mobile = accounts.mobileOf(talent.id());
			assertThatThrownBy(() -> api.talentContact(poster, application).andExpect(content().string(not(containsString("simulated")))))
					.hasMessageContaining("simulated logging failure");
			assertThatThrownBy(() -> api.posterContact(talent, application)).hasMessageContaining("simulated logging failure");
			assertThat(logRows()).isZero();
			assertThat(mobile).isNotBlank();
		}
		finally {
			jdbc.sql("DROP TRIGGER test_refuse_log ON hiring.contact_access_log").update();
			jdbc.sql("DROP FUNCTION hiring.test_refuse_log()").update();
		}
	}

	// ---- who can ask ---------------------------------------------------------------------------------------------------------

	@Test
	void nobodyElseCanReachAContactAndEachSideOnlyReachesTheOther() throws Exception {
		TestAccount otherPoster = accounts.create();
		TestAccount otherTalent = api.talent("Bela", "photographer");

		for (TestAccount stranger : new TestAccount[] { otherPoster, otherTalent }) {
			api.talentContact(stranger, application).andExpect(status().isNotFound());
			api.posterContact(stranger, application).andExpect(status().isNotFound());
		}
		api.posterContact(poster, application).andExpect(status().isNotFound()); // the Poster cannot ask for "the Poster's" contact
		api.talentContact(talent, application).andExpect(status().isNotFound()); // nor the Talent for their own
		api.talentContact(poster, UUID.randomUUID()).andExpect(status().isNotFound());
		api.talentContact(null, application).andExpect(status().isUnauthorized());
		api.posterContact(null, application).andExpect(status().isUnauthorized());
		assertThat(logRows()).isZero();
	}

	@Test
	void aRefusalNeverContainsAContactDetail() throws Exception {
		api.removeFromShortlist(poster, application).andExpect(status().isNoContent());
		String number = accounts.mobileOf(talent.id()).substring(3);

		ResultActions refused = api.talentContact(poster, application);

		refused.andExpect(status().isConflict()).andExpect(content().string(not(containsString(number))));
	}

}
