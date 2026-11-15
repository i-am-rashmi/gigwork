package com.gigwork.core.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.gigs.GigExpired;
import com.gigwork.core.hiring.HiringApiSupport.Shortlisted;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/** Cancelling a confirmed hire (FR-27a): the booking is released and the gig reopens before its start, expires after. */
@IdentityIntegrationTest
@RecordApplicationEvents
class HireCancelApiTest {

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
	ApplicationEvents events;

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
		gig = api.openGig(poster);
		Shortlisted s = api.shortlisted(poster, gig, "Asha");
		talent = s.talent();
		application = s.application();
		hire = HiringApiSupport.id(api.startHire(poster, application).andExpect(status().isCreated()));
		api.confirmHire(talent, hire).andExpect(status().isOk());
		events.clear();
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	private String gigStatus() {
		return jdbc.sql("SELECT status FROM gigs.gig WHERE id = :id").param("id", gig).query(String.class).single();
	}

	private int activeBookings() {
		return jdbc.sql("SELECT count(*) FROM availability.booking WHERE gig_id = :g AND status = 'ACTIVE'").param("g", gig)
				.query(Integer.class).single();
	}

	private TestAccount fresh(TestAccount account) {
		return new TestAccount(account.id(), accounts.token(account.id()));
	}

	@Test
	void theTalentCancelsBeforeTheStartAndTheGigReopensAndTheBookingIsReleased() throws Exception {
		api.cancelHire(talent, hire, "{\"reason\":\"  I'm ill  \"}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancelledBy").value("TALENT")).andExpect(jsonPath("$.cancelReason").value("I'm ill"));

		assertThat(gigStatus()).isEqualTo("OPEN");
		assertThat(jdbc.sql("SELECT closed_at IS NULL FROM gigs.gig WHERE id = :id").param("id", gig).query(Boolean.class).single()).isTrue();
		assertThat(activeBookings()).isZero();
		assertThat(events.stream(HireCancelled.class).toList()).singleElement().satisfies(e -> {
			assertThat(e.cancelledBy()).isEqualTo(HireSide.TALENT);
			assertThat(e.reason()).isEqualTo("I'm ill");
		});
	}

	@Test
	void thePosterCanCancelToo() throws Exception {
		api.cancelHire(poster, hire, "{\"reason\":\"Plans changed\"}").andExpect(status().isOk()).andExpect(jsonPath("$.cancelledBy").value("POSTER"));
		assertThat(gigStatus()).isEqualTo("OPEN");
	}

	@Test
	void afterTheCancellationThePosterCanHireSomeoneElse() throws Exception {
		api.cancelHire(talent, hire, "{\"reason\":\"Ill\"}").andExpect(status().isOk());
		Shortlisted other = api.shortlisted(poster, gig, "Bela");

		UUID second = HiringApiSupport.id(api.startHire(poster, other.application()).andExpect(status().isCreated()));
		api.confirmHire(other.talent(), second).andExpect(status().isOk());

		assertThat(gigStatus()).isEqualTo("FILLED");
		assertThat(activeBookings()).isEqualTo(1);
	}

	@Test
	void theTalentIsFreeToBeHiredForAnOverlappingGigAfterCancelling() throws Exception {
		api.cancelHire(talent, hire, "{\"reason\":\"Ill\"}").andExpect(status().isOk());
		UUID same = api.openGig(poster);
		UUID app = api.applyOk(talent, same);
		api.shortlist(poster, app, false).andExpect(status().isOk());
		UUID second = HiringApiSupport.id(api.startHire(poster, app).andExpect(status().isCreated()));

		api.confirmHire(talent, second).andExpect(status().isOk());
	}

	@Test
	void cancellingAfterTheGigStartedExpiresTheGigInsteadOfReopeningIt() throws Exception {
		clock.advance(Duration.ofDays(4)); // the gig started three days in

		api.cancelHire(fresh(poster), hire, "{\"reason\":\"No show\"}").andExpect(status().isOk());

		assertThat(gigStatus()).isEqualTo("EXPIRED");
		assertThat(jdbc.sql("SELECT closed_at IS NOT NULL FROM gigs.gig WHERE id = :id").param("id", gig).query(Boolean.class).single()).isTrue();
		assertThat(activeBookings()).isZero();
		assertThat(events.stream(GigExpired.class).count()).isEqualTo(1);
	}

	@Test
	void aReasonIsRequiredAndAtMost500Characters() throws Exception {
		api.cancelHire(talent, hire, "{}").andExpect(status().isBadRequest());
		api.cancelHire(talent, hire, "{\"reason\":\"   \"}").andExpect(status().isBadRequest());
		api.cancelHire(talent, hire, "{\"reason\":\"" + "x".repeat(501) + "\"}").andExpect(status().isBadRequest());
		api.cancelHire(talent, hire, "{\"reason\":\"" + "é".repeat(500) + "\"}").andExpect(status().isOk());
	}

	@Test
	void onlyAConfirmedHireCanBeCancelledAndOnlyByItsSides() throws Exception {
		api.cancelHire(accounts.create(), hire, "{\"reason\":\"x\"}").andExpect(status().isNotFound());
		api.cancelHire(api.talent("Bela", "photographer"), hire, "{\"reason\":\"x\"}").andExpect(status().isNotFound());
		api.cancelHire(null, hire, "{\"reason\":\"x\"}").andExpect(status().isUnauthorized());
		api.cancelHire(talent, UUID.randomUUID(), "{\"reason\":\"x\"}").andExpect(status().isNotFound());
		assertThat(gigStatus()).isEqualTo("FILLED");

		api.cancelHire(talent, hire, "{\"reason\":\"x\"}").andExpect(status().isOk());
		api.cancelHire(poster, hire, "{\"reason\":\"x\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_NOT_CONFIRMED"));

		UUID pendingGig = api.openGig(poster);
		Shortlisted s = api.shortlisted(poster, pendingGig, "Chitra");
		UUID pending = HiringApiSupport.id(api.startHire(poster, s.application()).andExpect(status().isCreated()));
		api.cancelHire(poster, pending, "{\"reason\":\"x\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HIRE_NOT_CONFIRMED"));
	}

}
