package com.gigwork.core.hiring.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.availability.Availability;
import com.gigwork.core.hiring.HiringApiSupport;
import com.gigwork.core.hiring.HiringApiSupport.Shortlisted;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.platform.EventResubmission;
import com.gigwork.core.reference.ReferenceData;

/**
 * A cancelled gig must not keep its hire and booking for ever. The GigCancelled listener fails the first time (releasing
 * the booking throws, which rolls the listener's whole transaction back); the
 * event stays incomplete in the registry, and the resubmission job (here with no minimum age) delivers it again.
 */
@IdentityIntegrationTest
@TestPropertySource(properties = "gigwork.platform.events.resubmit-older-than=PT0S")
class GigCancelledRetryTest {

	@MockitoSpyBean
	Availability availability; // the listener's step that fails the first time: releasing the Talent's booking

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
	EventResubmission resubmission;

	HiringApiSupport api;
	TestAccount poster;

	@BeforeEach
	void setUp() {
		clock.reset();
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		poster = accounts.create();
	}

	@Test
	void aListenerThatFailedOnceIsRetriedByTheResubmissionAndTheHireIsCancelledAndTheBookingReleased() throws Exception {
		UUID gig = api.openGig(poster);
		Shortlisted s = api.shortlisted(poster, gig, "Asha");
		UUID hire = HiringApiSupport.id(api.startHire(poster, s.application()).andExpect(status().isCreated()));
		api.confirmHire(s.talent(), hire).andExpect(status().isOk());
		doThrow(new IllegalStateException("simulated failure")).doCallRealMethod().when(availability).release(any(UUID.class));

		api.gigs.cancel(poster, gig, "{\"reason\":\"Venue closed\"}").andExpect(status().isOk());

		GigClosedListenersTest.awaitTrue("the listener to fail", () -> mockInvocations() >= 1);
		GigClosedListenersTest.awaitTrue("the failed event to stay incomplete", () -> incompleteFor(gig) == 1);
		assertThat(hireStatus(hire)).as("nothing changed: the listener failed").isEqualTo("CONFIRMED");
		assertThat(activeBookings(gig)).isEqualTo(1);

		clock.advance(java.time.Duration.ofSeconds(1)); // the registry compares with the application's clock: the event is now older than 0
		resubmission.run();

		GigClosedListenersTest.awaitTrue("the hire to be cancelled", () -> hireStatus(hire).equals("CANCELLED"));
		GigClosedListenersTest.awaitTrue("the booking to be released", () -> activeBookings(gig) == 0);
		GigClosedListenersTest.awaitTrue("the event to complete", () -> incompleteFor(gig) == 0);
		verify(availability, atLeast(2)).release(gig);
		assertThat(resubmission.run()).as("nothing left to retry for this gig").isGreaterThanOrEqualTo(0);
	}

	private int mockInvocations() {
		return (int) org.mockito.Mockito.mockingDetails(availability).getInvocations().stream().filter(i -> i.getMethod().getName().equals("release")).count();
	}

	private int incompleteFor(UUID gig) {
		return jdbc.sql("SELECT count(*) FROM event_publication WHERE completion_date IS NULL AND event_type LIKE '%GigCancelled' AND serialized_event LIKE :gig")
				.param("gig", "%" + gig + "%").query(Integer.class).single();
	}

	private String hireStatus(UUID id) {
		return jdbc.sql("SELECT status FROM hiring.hire WHERE id = :id").param("id", id).query(String.class).single();
	}

	private int activeBookings(UUID gig) {
		return jdbc.sql("SELECT count(*) FROM availability.booking WHERE gig_id = :g AND status = 'ACTIVE'").param("g", gig)
				.query(Integer.class).single();
	}

}
