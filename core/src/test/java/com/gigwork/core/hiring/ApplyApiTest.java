package com.gigwork.core.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.availability.Availability;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/** Applying to a gig (FR-23, NFR-39): the eligibility rules, the busy flow, the note, re-applying and the limit. */
@IdentityIntegrationTest
class ApplyApiTest {

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
	Availability availability;

	HiringApiSupport api;
	TestAccount poster;
	TestAccount talent;
	UUID gig;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		poster = accounts.create();
		talent = api.talent("Asha", "photographer");
		gig = api.openGig(poster);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	/** The same account with a token issued at the (moved) test clock's time. */
	private TestAccount fresh(TestAccount account) {
		return new TestAccount(account.id(), accounts.token(account.id()));
	}

	private int submissions(TestAccount account) {
		return jdbc.sql("SELECT count(*) FROM hiring.application_submission WHERE talent_id = :t").param("t", account.id())
				.query(Integer.class).single();
	}

	// ---- the happy path ------------------------------------------------------------------------------------------------------

	@Test
	void aTalentAppliesWithANoteAndGetsTheirApplicationBack() throws Exception {
		api.apply(talent, gig, "{\"note\":\"  I shot three weddings at this venue.  \"}").andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.gigId").value(gig.toString()))
				.andExpect(jsonPath("$.busyWarningShown").value(false));

		var row = jdbc.sql("SELECT note, status, poster_id, talent_id FROM hiring.application WHERE gig_id = :g").param("g", gig)
				.query().singleRow();
		assertThat(row.get("note")).isEqualTo("I shot three weddings at this venue.");
		assertThat(row.get("poster_id")).isEqualTo(poster.id());
		assertThat(row.get("talent_id")).isEqualTo(talent.id());
		assertThat(submissions(talent)).isEqualTo(1);
	}

	@Test
	void theNoteIsOptionalAndABlankNoteIsNoNote() throws Exception {
		api.apply(talent, gig, "").andExpect(status().isCreated());
		TestAccount other = api.talent("Bela", "photographer");
		api.apply(other, gig, "{\"note\":\"   \"}").andExpect(status().isCreated());
		assertThat(jdbc.sql("SELECT count(*) FROM hiring.application WHERE gig_id = :g AND note IS NULL").param("g", gig)
				.query(Integer.class).single()).isEqualTo(2);
	}

	@Test
	void anApplicationIsTwiceForOneGigRefusedAndDoesNotCount() throws Exception {
		api.apply(talent, gig).andExpect(status().isCreated());
		api.apply(talent, gig).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_APPLIED"))
				.andExpect(jsonPath("$.detail").value("You've already applied to this gig."));
		assertThat(submissions(talent)).isEqualTo(1);
	}

	// ---- the eligibility rules, one by one ---------------------------------------------------------------------------------

	@Test
	void aVisitorCannotApply() throws Exception {
		api.apply(null, gig).andExpect(status().isUnauthorized());
	}

	@Test
	void aTalentWithoutAProfileCannotApply() throws Exception {
		api.apply(accounts.create(), gig).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROFILE_NOT_ACTIVE"));
	}

	@Test
	void anIncompleteProfileCannotApply() throws Exception {
		TestAccount incomplete = accounts.create();
		api.profiles.put(incomplete, "work", api.profiles.simpleWork(api.profiles.categoryId("photographer"))).andExpect(status().isOk());
		api.apply(incomplete, gig).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROFILE_NOT_ACTIVE"));
	}

	@Test
	void aHiddenProfileCannotApplyUntilItIsVisibleAgain() throws Exception {
		api.profiles.put(talent, "visibility", "{\"hidden\":true}").andExpect(status().isOk());
		api.apply(talent, gig).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROFILE_NOT_ACTIVE"));
		api.profiles.put(talent, "visibility", "{\"hidden\":false}").andExpect(status().isOk());
		api.apply(talent, gig).andExpect(status().isCreated());
	}

	@Test
	void aGigThatDoesNotExistIsNotFound() throws Exception {
		api.apply(talent, UUID.randomUUID()).andExpect(status().isNotFound());
	}

	@Test
	void aCancelledFilledOrExpiredGigIsNotOpenForApplications() throws Exception {
		for (String status : new String[] { "CANCELLED", "FILLED", "EXPIRED" }) {
			UUID closed = api.openGig(poster);
			jdbc.sql("UPDATE gigs.gig SET status = :s, closed_at = CASE WHEN :s = 'FILLED' THEN NULL ELSE now() END WHERE id = :id").param("s", status).param("id", closed).update();
			api.apply(talent, closed).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GIG_NOT_OPEN"));
		}
		assertThat(submissions(talent)).isZero();
	}

	@Test
	void aGigThatHasStartedIsNotOpenForApplicationsEvenBeforeTheExpiryJobRan() throws Exception {
		clock.advance(Duration.ofDays(4)); // the gig started three days from now; no job has run
		api.apply(fresh(talent), gig).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GIG_NOT_OPEN"));
	}

	@Test
	void aPosterCannotApplyToTheirOwnGig() throws Exception {
		TestAccount both = api.talent("Chitra", "photographer");
		UUID own = api.openGig(both);
		api.apply(both, own).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OWN_GIG"));
	}

	@Test
	void theGigsCategoryMustBeOnTheProfileButSpecialisationAndLanguageDoNotRestrict() throws Exception {
		TestAccount anchor = api.talent("Dev", "anchor");
		api.apply(anchor, gig).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_ON_PROFILE"));

		UUID weddings = api.profiles.specialisationId("photographer", "wedding");
		UUID specialised = api.openGig(poster, api.gigs.onSite().specialisation(weddings));
		api.apply(talent, specialised).andExpect(status().isCreated()); // the Talent listed no specialisation

		UUID withLanguage = api.openGig(poster, api.gigs.onSite().category(api.profiles.categoryId("anchor")).language("kn"));
		api.apply(anchor, withLanguage).andExpect(status().isCreated()); // the Talent listed no language
	}

	// ---- the note -------------------------------------------------------------------------------------------------------------

	@Test
	void aNoteOf500CharactersIsFineAndOneMoreIsRefused() throws Exception {
		api.apply(talent, gig, "{\"note\":\"" + "é".repeat(500) + "\"}").andExpect(status().isCreated());
		TestAccount other = api.talent("Bela", "photographer");
		api.apply(other, gig, "{\"note\":\"" + "x".repeat(501) + "\"}").andExpect(status().isBadRequest());
		assertThat(submissions(other)).isZero();
	}

	@Test
	void aNoteWithContactDetailsIsRefused() throws Exception {
		for (String note : new String[] { "Call me on 98450 12345", "mail me at asha@example.com", "wa.me/919845012345" }) {
			api.apply(talent, gig, "{\"note\":\"" + note + "\"}").andExpect(status().isBadRequest());
		}
		assertThat(submissions(talent)).isZero();
	}

	// ---- the busy warning ----------------------------------------------------------------------------------------------------

	@Test
	void aBusyBlockRefusesTheFirstAttemptWithThePeriodsAndTheSecondOneSucceedsAndRemembersTheWarning() throws Exception {
		var start = clock.instant().plus(Duration.ofDays(3)).plus(Duration.ofHours(2));
		api.busyBlock(talent, start, start.plus(Duration.ofHours(2))).andExpect(status().isCreated());

		api.apply(talent, gig).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BUSY"))
				.andExpect(jsonPath("$.periods", hasSize(1))).andExpect(jsonPath("$.periods[0].kind").value("BUSY_BLOCK"))
				.andExpect(jsonPath("$.periods[0].start").exists()).andExpect(jsonPath("$.periods[0].end").exists());
		assertThat(submissions(talent)).isZero();
		assertThat(jdbc.sql("SELECT count(*) FROM hiring.application WHERE talent_id = :t").param("t", talent.id())
				.query(Integer.class).single()).isZero();

		api.applyAcknowledgingBusy(talent, gig).andExpect(status().isCreated()).andExpect(jsonPath("$.busyWarningShown").value(true));
		assertThat(jdbc.sql("SELECT busy_warning_shown FROM hiring.application WHERE talent_id = :t").param("t", talent.id())
				.query(Boolean.class).single()).isTrue();
	}

	@Test
	void aBookingAlsoWarns() throws Exception {
		UUID other = api.openGig(poster);
		var start = clock.instant().plus(Duration.ofDays(3));
		availability.book(talent.id(), other, start, start.plus(Duration.ofHours(8)));
		api.apply(talent, gig).andExpect(status().isConflict()).andExpect(jsonPath("$.periods[0].kind").value("BOOKING"));
	}

	@Test
	void backToBackPeriodsDoNotWarnAndAcknowledgingWithoutAConflictStoresNoWarning() throws Exception {
		var gigEnd = clock.instant().plus(Duration.ofDays(3)).plus(Duration.ofHours(8));
		api.busyBlock(talent, gigEnd, gigEnd.plus(Duration.ofHours(1))).andExpect(status().isCreated()); // starts as the gig ends
		api.applyAcknowledgingBusy(talent, gig).andExpect(status().isCreated()).andExpect(jsonPath("$.busyWarningShown").value(false));
	}

	// ---- applying again after a withdrawal and the limit ---------------------------------------------------------------

	@Test
	void applyingAgainAfterWithdrawingReactivatesTheSameApplicationAndCountsAsASubmission() throws Exception {
		UUID first = HiringApiSupport.id(api.apply(talent, gig, "{\"note\":\"first\"}").andExpect(status().isCreated()));
		jdbc.sql("UPDATE hiring.application SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = :id").param("id", first).update();

		UUID again = HiringApiSupport.id(api.apply(talent, gig, "{\"note\":\"second\"}").andExpect(status().isCreated()));

		assertThat(again).isEqualTo(first);
		var row = jdbc.sql("SELECT status, note, withdrawn_at FROM hiring.application WHERE id = :id").param("id", first).query().singleRow();
		assertThat(row.get("status")).isEqualTo("ACTIVE");
		assertThat(row.get("note")).isEqualTo("second");
		assertThat(row.get("withdrawn_at")).isNull();
		assertThat(submissions(talent)).isEqualTo(2);
	}

	@Test
	void theThirtyFirstSubmissionInADayIsRefusedAndTheWindowRolls() throws Exception {
		for (int i = 0; i < 30; i++) {
			api.apply(talent, api.openGig(poster)).andExpect(status().isCreated());
		}
		UUID last = api.openGig(poster);
		api.apply(talent, last).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("APPLY_LIMIT_REACHED"))
				.andExpect(jsonPath("$.detail").value("You can apply 30 times in 24 hours, and you've reached that. Try again later."));
		assertThat(submissions(talent)).isEqualTo(30);

		clock.advance(Duration.ofHours(24).plusMinutes(1)); // the first 30 leave the window
		UUID later = api.openGig(fresh(poster), api.gigs.onSite().startsIn(Duration.ofDays(5)));
		api.apply(fresh(talent), later).andExpect(status().isCreated());
	}

	@Test
	void theLimitIsPerTalent() throws Exception {
		TestAccount other = api.talent("Bela", "photographer");
		for (int i = 0; i < 3; i++) {
			api.apply(talent, api.openGig(poster)).andExpect(status().isCreated());
		}
		api.apply(other, gig).andExpect(status().isCreated());
		assertThat(submissions(other)).isEqualTo(1);
	}

}
