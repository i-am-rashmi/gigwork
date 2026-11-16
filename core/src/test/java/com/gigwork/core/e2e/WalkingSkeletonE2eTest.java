package com.gigwork.core.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.gigs.GigApiSupport.Gig;
import com.gigwork.core.hiring.HiringApiSupport;
import com.gigwork.core.identity.support.CapturingOtpSender;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.profile.ProfileApiSupport;
import com.gigwork.core.reference.ReferenceData;

/**
 * The walking skeleton from end to end over HTTP: a Poster and a Talent log in with the real OTP endpoints and go from
 * their names to a confirmed hire. Only the OTP sender (captures the code) and the clock (fixed) are test doubles.
 */
@IdentityIntegrationTest
class WalkingSkeletonE2eTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	ReferenceData reference;
	@Autowired
	TestAccounts accounts;
	@Autowired
	MutableClock clock;
	@Autowired
	CapturingOtpSender otp;

	@BeforeEach
	void setUp() {
		clock.reset();
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void fromLoginToAConfirmedHire() throws Exception {
		WalkingSkeletonSupport flow = new WalkingSkeletonSupport(mvc, otp);
		HiringApiSupport api = new HiringApiSupport(mvc, reference, accounts, clock);
		ProfileApiSupport profiles = api.profiles;

		// 1. Two people log in through the OTP endpoints, name themselves and choose what they share (FR-1, FR-2)
		TestAccount poster = flow.login();
		TestAccount talent = flow.login();
		assertThat(poster.id()).isNotEqualTo(talent.id());
		flow.name(poster, "Priya").andExpect(status().isOk());
		flow.name(talent, "Asha").andExpect(status().isOk());
		api.sharesContact(poster);
		api.sharesContact(talent);

		// 2. The Talent builds a profile: work, location, portfolio (FR-5 to FR-9)
		profiles.put(talent, "work", profiles.simpleWork(profiles.categoryId("photographer"))).andExpect(status().isOk());
		profiles.put(talent, "location",
				ProfileApiSupport.locationByLocality(api.gigs.indiranagar(), 10, true)).andExpect(status().isOk());
		profiles.put(talent, "portfolio", ProfileApiSupport.links("instagram.com/asha")).andExpect(status().isOk());
		profiles.view(talent.id(), poster.header()).andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Asha"));

		// 3. The Poster publishes a gig (FR-12)
		Gig gigRequest = api.gigs.onSite();
		UUID gig = api.gigs.publishOk(poster, gigRequest);

		// 4. The Talent is busy while the gig runs: the first application is refused, the acknowledged one is accepted (FR-7, FR-23)
		api.busyBlock(talent, gigRequest.start().plus(Duration.ofHours(1)), gigRequest.start().plus(Duration.ofHours(3)))
				.andExpect(status().isCreated());
		api.apply(talent, gig).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BUSY"))
				.andExpect(jsonPath("$.periods[0].kind").value("BUSY_BLOCK"));
		UUID application = WalkingSkeletonSupport.id(api.applyAcknowledgingBusy(talent, gig).andExpect(status().isCreated()));

		// 5. The Poster sees the applicant, without contact details (FR-24, NFR-34)
		api.applicants(poster, gig, "").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].id").value(application.toString()))
				.andExpect(jsonPath("$.items[0].talentId").value(talent.id().toString()))
				.andExpect(jsonPath("$.items[0].profile.firstName").value("Asha"))
				.andExpect(jsonPath("$.items[0].shortlisted").value(false));
		String listing = WalkingSkeletonSupport.body(api.applicants(poster, gig, ""));
		assertThat(listing).doesNotContainPattern("[6-9][0-9]{9}");

		// 6. The Poster shortlists and shares their contact; the Talent can see it, the Poster cannot see the Talent yet (FR-25, FR-26)
		api.shortlist(poster, application, true).andExpect(status().isOk());
		api.posterContact(talent, application).andExpect(status().isOk())
				.andExpect(jsonPath("$.mobile").value(accounts.mobileOf(poster.id())));
		api.talentContact(poster, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTACT_NOT_AVAILABLE"));

		// 7. The Talent consents, and now the Poster sees theirs (FR-25a, FR-26)
		api.consent(talent, application, true).andExpect(status().isOk());
		api.talentContact(poster, application).andExpect(status().isOk())
				.andExpect(jsonPath("$.mobile").value(accounts.mobileOf(talent.id())));

		// 8. The Poster starts a hire; the gig is held, so it cannot be edited meanwhile (FR-27)
		UUID hire = WalkingSkeletonSupport.id(api.startHire(poster, application).andExpect(status().isCreated()));
		api.gigs.edit(poster, gig, api.gigs.onSite().title("Changed")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Hire pending"));

		// 9. The Talent confirms: the gig is FILLED and the booking is in the Talent's calendar (FR-28, FR-19)
		api.confirmHire(talent, hire).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
		api.gigs.get(poster, "/gigs/" + gig).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FILLED"));
		LocalDate day = gigRequest.start().atZone(ZoneOffset.UTC).toLocalDate();
		flow.calendar(talent, day.minusDays(1).toString(), day.plusDays(1).toString()).andExpect(status().isOk())
				.andExpect(jsonPath("$.entries[*].type", hasItem("BOOKING")))
				.andExpect(jsonPath("$.entries[?(@.type=='BOOKING')].gigId", hasItem(gig.toString())));

		// 10. A confirmed application can no longer be withdrawn (FR-29)
		api.withdraw(talent, application).andExpect(status().isConflict());
		api.hire(poster, hire).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
	}

}
