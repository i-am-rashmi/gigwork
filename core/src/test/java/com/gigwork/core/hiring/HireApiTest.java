package com.gigwork.core.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.availability.Availability;
import com.gigwork.core.hiring.HiringApiSupport.Shortlisted;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/** Starting, confirming, declining and withdrawing a hire (FR-27, FR-28, FR-29): who may, and what each step changes. */
@IdentityIntegrationTest
@RecordApplicationEvents
class HireApiTest {

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
	@Autowired
	ApplicationEvents events;

	HiringApiSupport api;
	TestAccount poster;
	TestAccount talent;
	UUID gig;
	UUID application;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		poster = accounts.create();
		gig = api.openGig(poster);
		Shortlisted s = api.shortlisted(poster, gig, "Asha");
		talent = s.talent();
		application = s.application();
		events.clear();
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	private UUID startedByPoster() throws Exception {
		return HiringApiSupport.id(api.startHire(poster, application).andExpect(status().isCreated()));
	}

	private String gigStatus(UUID id) {
		return jdbc.sql("SELECT status FROM gigs.gig WHERE id = :id").param("id", id).query(String.class).single();
	}

	private boolean held(UUID id) {
		return jdbc.sql("SELECT hire_pending FROM gigs.gig WHERE id = :id").param("id", id).query(Boolean.class).single();
	}

	private String hireStatus(UUID id) {
		return jdbc.sql("SELECT status FROM hiring.hire WHERE id = :id").param("id", id).query(String.class).single();
	}

	private int bookings(UUID gigId) {
		return jdbc.sql("SELECT count(*) FROM availability.booking WHERE gig_id = :g AND status = 'ACTIVE'").param("g", gigId)
				.query(Integer.class).single();
	}

	private static Instant instant(Object timestamp) {
		return timestamp instanceof java.time.OffsetDateTime o ? o.toInstant() : ((java.sql.Timestamp) timestamp).toInstant();
	}

	private TestAccount fresh(TestAccount account) {
		return new TestAccount(account.id(), accounts.token(account.id()));
	}

	// ---- starting --------------------------------------------------------------------------------------------------------

	@Test
	void thePosterStartsAHireThatExpiresIn24HoursAndHoldsTheGig() throws Exception {
		api.startHire(poster, application).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.startedBy").value("POSTER")).andExpect(jsonPath("$.applicationId").value(application.toString()));

		var row = jdbc.sql("SELECT started_at, expires_at FROM hiring.hire WHERE application_id = :a").param("a", application).query().singleRow();
		Instant started = instant(row.get("started_at"));
		Instant expires = instant(row.get("expires_at"));
		assertThat(Duration.between(started, expires)).isEqualTo(Duration.ofHours(24));
		assertThat(gigStatus(gig)).isEqualTo("OPEN");
		assertThat(held(gig)).isTrue();
		assertThat(events.stream(HireStarted.class).toList()).singleElement().satisfies(e -> {
			assertThat(e.startedBy()).isEqualTo(HireSide.POSTER);
			assertThat(e.talentId()).isEqualTo(talent.id());
			assertThat(e.posterId()).isEqualTo(poster.id());
		});
	}

	@Test
	void theTalentCanStartAHireToo() throws Exception {
		api.startHire(talent, application).andExpect(status().isCreated()).andExpect(jsonPath("$.startedBy").value("TALENT"));
		assertThat(held(gig)).isTrue();
	}

	@Test
	void aHireForAGigThatStartsSoonExpiresWhenTheGigStarts() throws Exception {
		UUID soon = api.openGig(poster, api.gigs.onSite().startsIn(Duration.ofHours(10)));
		Shortlisted s = api.shortlisted(poster, soon, "Bela");

		api.startHire(poster, s.application()).andExpect(status().isCreated());

		var row = jdbc.sql("SELECT expires_at, (SELECT start_at FROM gigs.gig WHERE id = :g) AS gig_start FROM hiring.hire WHERE application_id = :a")
				.param("a", s.application()).param("g", soon).query().singleRow();
		assertThat(row.get("expires_at")).isEqualTo(row.get("gig_start"));
	}

	@Test
	void onlyThePosterAndTheApplicantCanStartAndVisitorsAreRefused() throws Exception {
		api.startHire(accounts.create(), application).andExpect(status().isNotFound());
		api.startHire(api.talent("Bela", "photographer"), application).andExpect(status().isNotFound());
		api.startHire(poster, UUID.randomUUID()).andExpect(status().isNotFound());
		api.startHire(null, application).andExpect(status().isUnauthorized());
		assertThat(held(gig)).isFalse();
	}

	@Test
	void theTalentMustBeShortlistedAndTheApplicationActive() throws Exception {
		TestAccount other = api.talent("Bela", "photographer");
		UUID notShortlisted = api.applyOk(other, gig);
		api.startHire(poster, notShortlisted).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_SHORTLISTED"));
		api.startHire(other, notShortlisted).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_SHORTLISTED"));

		api.shortlist(poster, notShortlisted, false).andExpect(status().isOk());
		api.removeFromShortlist(poster, notShortlisted).andExpect(status().isNoContent());
		api.startHire(poster, notShortlisted).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_SHORTLISTED"));

		api.withdraw(other, notShortlisted).andExpect(status().isNoContent());
		api.startHire(poster, notShortlisted).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPLICATION_NOT_ACTIVE"));
		assertThat(held(gig)).isFalse();
	}

	@Test
	void aGigThatIsNotOpenCannotHaveAHire() throws Exception {
		for (String status : new String[] { "CANCELLED", "EXPIRED", "FILLED" }) {
			jdbc.sql("UPDATE gigs.gig SET status = :s, closed_at = CASE WHEN :s = 'FILLED' THEN NULL ELSE now() END WHERE id = :id")
					.param("s", status).param("id", gig).update();
			api.startHire(poster, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GIG_NOT_OPEN"));
		}
		assertThat(jdbc.sql("SELECT count(*) FROM hiring.hire WHERE gig_id = :g").param("g", gig).query(Integer.class).single()).isZero();
	}

	@Test
	void aGigCanHaveOnlyOnePendingHire() throws Exception {
		TestAccount other = api.talent("Bela", "photographer");
		UUID otherApplication = api.applyOk(other, gig);
		api.shortlist(poster, otherApplication, false).andExpect(status().isOk());
		startedByPoster();

		api.startHire(poster, otherApplication).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_ALREADY_PENDING"));
		api.startHire(poster, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_ALREADY_PENDING")); // same side again
		assertThat(jdbc.sql("SELECT count(*) FROM hiring.hire WHERE gig_id = :g").param("g", gig).query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void ifTheOtherSideAlreadyStartedTheHireAStartConfirmsIt() throws Exception {
		UUID hire = HiringApiSupport.id(api.startHire(talent, application).andExpect(status().isCreated()));

		api.startHire(poster, application).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.id").value(hire.toString()));

		assertThat(gigStatus(gig)).isEqualTo("FILLED");
		assertThat(bookings(gig)).isEqualTo(1);
		assertThat(events.stream(HireConfirmed.class).count()).isEqualTo(1);
	}

	// ---- confirming ------------------------------------------------------------------------------------------------------

	@Test
	void theTalentConfirmsAndInOneStepTheGigIsFilledAndTheyAreBooked() throws Exception {
		UUID hire = startedByPoster();
		events.clear();

		api.confirmHire(talent, hire).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.confirmedAt").exists());

		assertThat(gigStatus(gig)).isEqualTo("FILLED");
		assertThat(held(gig)).isFalse();
		var gigTimes = jdbc.sql("SELECT start_at, end_at FROM gigs.gig WHERE id = :g").param("g", gig).query().singleRow();
		var booking = jdbc.sql("SELECT id, account_id, lower(period) AS s, upper(period) AS e FROM availability.booking WHERE gig_id = :g AND status = 'ACTIVE'")
				.param("g", gig).query().singleRow();
		assertThat(booking.get("account_id")).isEqualTo(talent.id());
		assertThat(booking.get("s")).isEqualTo(gigTimes.get("start_at"));
		assertThat(booking.get("e")).isEqualTo(gigTimes.get("end_at"));
		assertThat(jdbc.sql("SELECT booking_id FROM hiring.hire WHERE id = :id").param("id", hire).query(UUID.class).single()).isEqualTo(booking.get("id"));
		assertThat(events.stream(HireConfirmed.class).toList()).singleElement().satisfies(e -> assertThat(e.hireId()).isEqualTo(hire));
	}

	@Test
	void thePosterConfirmsAHireTheTalentStarted() throws Exception {
		UUID hire = HiringApiSupport.id(api.startHire(talent, application).andExpect(status().isCreated()));

		api.confirmHire(poster, hire).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
		assertThat(gigStatus(gig)).isEqualTo("FILLED");
	}

	@Test
	void theStarterAndStrangersCannotConfirm() throws Exception {
		UUID hire = startedByPoster();

		api.confirmHire(poster, hire).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_WRONG_SIDE"));
		api.confirmHire(accounts.create(), hire).andExpect(status().isNotFound());
		api.confirmHire(api.talent("Bela", "photographer"), hire).andExpect(status().isNotFound());
		api.confirmHire(talent, UUID.randomUUID()).andExpect(status().isNotFound());
		api.confirmHire(null, hire).andExpect(status().isUnauthorized());
		assertThat(hireStatus(hire)).isEqualTo("PENDING");
		assertThat(bookings(gig)).isZero();
	}

	@Test
	void aHireCannotBeConfirmedTwice() throws Exception {
		UUID hire = startedByPoster();
		api.confirmHire(talent, hire).andExpect(status().isOk());

		api.confirmHire(talent, hire).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_NOT_PENDING"));
		assertThat(bookings(gig)).isEqualTo(1);
	}

	@Test
	void anExpiredHireCannotBeConfirmedEvenIfTheLapseJobHasNotRun() throws Exception {
		UUID hire = startedByPoster();
		clock.advance(Duration.ofHours(25));

		api.confirmHire(fresh(talent), hire).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_NOT_PENDING"));

		assertThat(hireStatus(hire)).isEqualTo("PENDING");
		assertThat(gigStatus(gig)).isEqualTo("OPEN");
		assertThat(bookings(gig)).isZero();
	}

	@Test
	void aHireCannotBeConfirmedOnceTheGigWasCancelled() throws Exception {
		UUID hire = startedByPoster();
		api.gigs.cancel(poster, gig, "{}").andExpect(status().isOk());

		api.confirmHire(talent, hire).andExpect(status().isConflict());

		assertThat(bookings(gig)).isZero();
		assertThat(gigStatus(gig)).isEqualTo("CANCELLED");
	}

	@Test
	void aBookingConflictRefusesTheConfirmationAndChangesNothing() throws Exception {
		UUID otherGig = api.openGig(poster); // the same time as the first gig
		var times = jdbc.sql("SELECT start_at, end_at FROM gigs.gig WHERE id = :g").param("g", gig).query().singleRow();
		availability.book(talent.id(), otherGig, instant(times.get("start_at")),
				instant(times.get("end_at")));
		UUID hire = startedByPoster();
		events.clear();

		api.confirmHire(talent, hire).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOKING_CONFLICT"));

		assertThat(hireStatus(hire)).isEqualTo("PENDING"); // everything rolled back
		assertThat(gigStatus(gig)).isEqualTo("OPEN");
		assertThat(held(gig)).isTrue();
		assertThat(bookings(gig)).isZero();
		assertThat(jdbc.sql("SELECT booking_id IS NULL FROM hiring.hire WHERE id = :id").param("id", hire).query(Boolean.class).single()).isTrue();
		assertThat(events.stream(HireConfirmed.class).count()).isZero();
		api.withdrawHire(poster, hire).andExpect(status().isOk()); // and the gig can be released again
		assertThat(held(gig)).isFalse();
	}

	@Test
	void aBusyBlockDoesNotStopAHire() throws Exception {
		var start = clock.instant().plus(Duration.ofDays(3));
		api.busyBlock(talent, start, start.plus(Duration.ofHours(2))).andExpect(status().isCreated());
		UUID hire = startedByPoster();

		api.confirmHire(talent, hire).andExpect(status().isOk());
	}

	// ---- declining and withdrawing ---------------------------------------------------------------------------------------

	@Test
	void theOtherSideDeclinesAndTheGigIsFreeAgain() throws Exception {
		UUID hire = startedByPoster();
		events.clear();

		api.declineHire(poster, hire).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_WRONG_SIDE"));
		api.declineHire(talent, hire).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DECLINED")).andExpect(jsonPath("$.endedAt").exists());

		assertThat(held(gig)).isFalse();
		assertThat(gigStatus(gig)).isEqualTo("OPEN");
		assertThat(events.stream(HireDeclined.class).count()).isEqualTo(1);
		api.declineHire(talent, hire).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_NOT_PENDING"));
		api.startHire(poster, application).andExpect(status().isCreated()); // a new hire for the same application
	}

	@Test
	void theStarterWithdrawsAndTheGigIsFreeAgain() throws Exception {
		UUID hire = startedByPoster();
		events.clear();

		api.withdrawHire(talent, hire).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_WRONG_SIDE"));
		api.withdrawHire(accounts.create(), hire).andExpect(status().isNotFound());
		api.withdrawHire(poster, hire).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));

		assertThat(held(gig)).isFalse();
		assertThat(events.stream(HireWithdrawn.class).count()).isEqualTo(1);
		api.confirmHire(talent, hire).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_NOT_PENDING"));
	}

	@Test
	void eitherSideCanReadTheirHireAndNobodyElse() throws Exception {
		UUID hire = startedByPoster();

		api.hire(poster, hire).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
		api.hire(talent, hire).andExpect(status().isOk());
		api.hire(accounts.create(), hire).andExpect(status().isNotFound());
		api.hire(null, hire).andExpect(status().isUnauthorized());
	}

	// ---- a gig that is held cannot be edited ----------------------------------------------------------------------------------

	@Test
	void whileAHireIsPendingTheGigCannotBeEditedAndAfterItEndsItCan() throws Exception {
		UUID hire = startedByPoster();

		api.gigs.edit(poster, gig, api.gigs.onSite().title("Changed")).andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("Hire pending"));

		api.withdrawHire(poster, hire).andExpect(status().isOk());
		api.gigs.edit(poster, gig, api.gigs.onSite().title("Changed")).andExpect(status().isOk());
	}

	// ---- an application with a hire cannot be withdrawn or un-shortlisted ----------------------------------------------

	@Test
	void whileAHireIsPendingOrConfirmedTheApplicationCannotBeWithdrawnNorTheTalentRemoved() throws Exception {
		UUID hire = startedByPoster();

		api.withdraw(talent, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_IN_PROGRESS"));
		api.removeFromShortlist(poster, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_IN_PROGRESS"));

		api.confirmHire(talent, hire).andExpect(status().isOk());
		api.withdraw(talent, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_IN_PROGRESS"));
		api.removeFromShortlist(poster, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_IN_PROGRESS"));
		assertThat(jdbc.sql("SELECT status FROM hiring.application WHERE id = :a").param("a", application).query(String.class).single()).isEqualTo("ACTIVE");
		assertThat(jdbc.sql("SELECT removed_at IS NULL FROM hiring.shortlist_entry WHERE application_id = :a").param("a", application)
				.query(Boolean.class).single()).isTrue();
	}

	@Test
	void afterTheHireEndedTheApplicationCanBeWithdrawnAgain() throws Exception {
		UUID hire = startedByPoster();
		api.declineHire(talent, hire).andExpect(status().isOk());

		api.removeFromShortlist(poster, application).andExpect(status().isNoContent());
		api.shortlist(poster, application, false).andExpect(status().isOk());
		api.withdraw(talent, application).andExpect(status().isNoContent());
	}

	@Test
	void everyConfirmedHireHasABooking() throws Exception {
		List<UUID> hires = new java.util.ArrayList<>();
		for (int i = 0; i < 2; i++) {
			UUID g = api.openGig(poster, api.gigs.onSite().startsIn(Duration.ofDays(5 + i * 2L)));
			Shortlisted s = api.shortlisted(poster, g, "T" + i);
			UUID hire = HiringApiSupport.id(api.startHire(poster, s.application()).andExpect(status().isCreated()));
			api.confirmHire(s.talent(), hire).andExpect(status().isOk());
			hires.add(hire);
		}
		assertThat(jdbc.sql("SELECT count(*) FROM hiring.hire WHERE status = 'CONFIRMED' AND booking_id IS NULL").query(Integer.class).single()).isZero();
	}

}
