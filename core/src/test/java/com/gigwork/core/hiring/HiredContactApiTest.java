package com.gigwork.core.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.hiring.HiringApiSupport.Shortlisted;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/**
 * The hired pair's contact access (FR-26): after the gig is filled only they can view each other's contacts (still
 * with the sharing and consent they gave), while the hire is confirmed and until 30 days after the gig ends.
 */
@IdentityIntegrationTest
class HiredContactApiTest {

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
	UUID hire;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		poster = accounts.create();
		api.sharesContact(poster);
		gig = api.openGig(poster);
		Shortlisted s = shareAndConsent(api.shortlisted(poster, gig, "Asha"), true);
		talent = s.talent();
		application = s.application();
		hire = HiringApiSupport.id(api.startHire(poster, application).andExpect(status().isCreated()));
		api.confirmHire(talent, hire).andExpect(status().isOk());
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	/** The Poster shares with this Talent (already, from the shortlisting with true) and the Talent consents. */
	private Shortlisted shareAndConsent(Shortlisted s, boolean consent) throws Exception {
		api.sharesContact(s.talent());
		api.posterShare(poster, s.application(), true).andExpect(status().isOk());
		if (consent) {
			api.consent(s.talent(), s.application(), true).andExpect(status().isOk());
		}
		return s;
	}

	private TestAccount fresh(TestAccount account) {
		return new TestAccount(account.id(), accounts.token(account.id()));
	}

	private Instant gigEnd() {
		Object end = jdbc.sql("SELECT end_at FROM gigs.gig WHERE id = :g").param("g", gig).query().singleRow().get("end_at");
		return end instanceof java.time.OffsetDateTime o ? o.toInstant() : ((java.sql.Timestamp) end).toInstant();
	}

	private int logRows() {
		return jdbc.sql("SELECT count(*) FROM hiring.contact_access_log WHERE application_id = :a").param("a", application)
				.query(Integer.class).single();
	}

	@Test
	void theHiredPairCanViewEachOthersContactAfterTheGigIsFilled() throws Exception {
		assertThat(jdbc.sql("SELECT status FROM gigs.gig WHERE id = :g").param("g", gig).query(String.class).single()).isEqualTo("FILLED");

		api.talentContact(poster, application).andExpect(status().isOk()).andExpect(jsonPath("$.mobile").value(accounts.mobileOf(talent.id())));
		api.posterContact(talent, application).andExpect(status().isOk()).andExpect(jsonPath("$.mobile").value(accounts.mobileOf(poster.id())));

		assertThat(logRows()).isEqualTo(2); // every view is logged
	}

	@Test
	void otherShortlistedTalentLoseAccessWhenTheGigIsFilled() throws Exception {
		// shortlisted and consented while the gig is still open...
		UUID second = api.openGig(poster);
		Shortlisted open = shareAndConsent(api.shortlisted(poster, second, "Bela"), true);
		api.talentContact(poster, open.application()).andExpect(status().isOk());
		api.posterContact(open.talent(), open.application()).andExpect(status().isOk());
		// ...a hire for another Talent fills the gig
		Shortlisted other = shareAndConsent(api.shortlisted(poster, second, "Chitra"), true);
		UUID otherHire = HiringApiSupport.id(api.startHire(poster, other.application()).andExpect(status().isCreated()));
		api.confirmHire(other.talent(), otherHire).andExpect(status().isOk());

		api.talentContact(poster, open.application()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));
		api.posterContact(open.talent(), open.application()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));
		api.talentContact(poster, other.application()).andExpect(status().isOk()); // the hired one still can
	}

	@Test
	void accessEndsThirtyDaysAfterTheGigEnds() throws Exception {
		Instant end = gigEnd();

		clock.set(end.plus(Duration.ofDays(30)).minus(Duration.ofHours(1)));
		api.talentContact(fresh(poster), application).andExpect(status().isOk());
		api.posterContact(fresh(talent), application).andExpect(status().isOk());

		clock.set(end.plus(Duration.ofDays(30)));
		api.talentContact(fresh(poster), application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));
		api.posterContact(fresh(talent), application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));
	}

	@Test
	void theSharingAndConsentTheyGaveStillDecide() throws Exception {
		api.consent(talent, application, false).andExpect(status().isOk());
		api.talentContact(poster, application).andExpect(status().isConflict()); // the Talent withdrew consent: not even the hired Poster
		api.posterContact(talent, application).andExpect(status().isOk());

		api.posterShare(poster, application, false).andExpect(status().isOk());
		api.posterContact(talent, application).andExpect(status().isConflict());
	}

	@Test
	void aCancelledHireEndsTheHiredAccessOnceTheGigIsNoLongerOpenOrFilled() throws Exception {
		clock.set(gigEnd().plus(Duration.ofHours(1))); // the gig is over
		api.cancelHire(fresh(poster), hire, "{\"reason\":\"No show\"}").andExpect(status().isOk()); // the gig expires

		api.talentContact(fresh(poster), application).andExpect(status().isConflict());
		api.posterContact(fresh(talent), application).andExpect(status().isConflict());
	}

	@Test
	void ifTheHireIsCancelledBeforeTheStartTheGigReopensAndTheOpenRulesApplyAgain() throws Exception {
		api.cancelHire(talent, hire, "{\"reason\":\"Ill\"}").andExpect(status().isOk());

		api.talentContact(poster, application).andExpect(status().isOk()); // open again: still shortlisted and consented
	}

	@Test
	void aTalentWhoWasNeverHiredGetsNoExtraAccessFromAnotherTalentsHire() throws Exception {
		// the hired pair is `talent`; a stranger to the gig is refused outright
		TestAccount stranger = api.talent("Dev", "photographer");
		api.posterContact(stranger, application).andExpect(status().isNotFound());
		api.talentContact(stranger, application).andExpect(status().isNotFound());
	}

}
