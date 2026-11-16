package com.gigwork.core.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.jayway.jsonpath.JsonPath;

/** The failure scenarios of the walking skeleton, each over HTTP with accounts that logged in through the OTP endpoints. */
@IdentityIntegrationTest
class WalkingSkeletonFailuresE2eTest {

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

	WalkingSkeletonSupport flow;
	HiringApiSupport api;
	TestAccount poster;
	TestAccount talent;
	Gig gigRequest;
	UUID gig;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		flow = new WalkingSkeletonSupport(mvc, otp);
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		ProfileApiSupport profiles = api.profiles;
		poster = flow.login();
		talent = flow.login();
		flow.name(poster, "Priya").andExpect(status().isOk());
		flow.name(talent, "Asha").andExpect(status().isOk());
		api.sharesContact(poster);
		api.sharesContact(talent);
		profiles.put(talent, "work", profiles.simpleWork(profiles.categoryId("photographer"))).andExpect(status().isOk());
		profiles.put(talent, "location", ProfileApiSupport.locationByLocality(api.gigs.indiranagar(), 10, true))
				.andExpect(status().isOk());
		profiles.put(talent, "portfolio", ProfileApiSupport.links("https://instagram.com/asha")).andExpect(status().isOk());
		gigRequest = api.gigs.onSite();
		gig = api.gigs.publishOk(poster, gigRequest);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	private UUID confirmedHire(UUID application) throws Exception {
		api.shortlist(poster, application, true).andExpect(status().isOk());
		api.consent(talent, application, true).andExpect(status().isOk());
		UUID hire = WalkingSkeletonSupport.id(api.startHire(poster, application).andExpect(status().isCreated()));
		api.confirmHire(talent, hire).andExpect(status().isOk());
		return hire;
	}

	private String gigStatus() throws Exception {
		return JsonPath.read(WalkingSkeletonSupport.body(api.gigs.get(poster, "/gigs/" + gig).andExpect(status().isOk())), "$.status");
	}

	private boolean hasBooking() throws Exception {
		LocalDate day = gigRequest.start().atZone(ZoneOffset.UTC).toLocalDate();
		String json = WalkingSkeletonSupport.body(flow.calendar(talent, day.minusDays(1).toString(), day.plusDays(1).toString())
				.andExpect(status().isOk()));
		return !JsonPath.<java.util.List<Object>>read(json, "$.entries[?(@.type=='BOOKING')]").isEmpty();
	}

	@Test
	void applyingTwiceIsRefused() throws Exception {
		api.apply(talent, gig).andExpect(status().isCreated());

		api.apply(talent, gig).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_APPLIED"));
		api.applicants(poster, gig, "").andExpect(jsonPath("$.items", hasSize(1)));
	}

	@Test
	void aVisitorSeesNoNameAndOnlyTheLinkTypes() throws Exception {
		flow.publicProfile(talent.id(), null).andExpect(status().isOk()).andExpect(jsonPath("$.firstName").doesNotExist())
				.andExpect(jsonPath("$.portfolio[0].type").value("INSTAGRAM")).andExpect(jsonPath("$.portfolio[0].url").doesNotExist());
		String visitor = WalkingSkeletonSupport.body(flow.publicProfile(talent.id(), null));
		assertThat(visitor).doesNotContain("Asha", "instagram.com/asha").doesNotContainPattern("[6-9][0-9]{9}");

		flow.publicProfile(talent.id(), poster.header()).andExpect(jsonPath("$.firstName").value("Asha"))
				.andExpect(jsonPath("$.portfolio[0].url").value("https://instagram.com/asha"));
	}

	@Test
	void aPausedProfileIs404ToThePublicButTheApplicantListStillShowsIt() throws Exception {
		api.apply(talent, gig).andExpect(status().isCreated());
		api.profiles.put(talent, "visibility", "{\"hidden\":true}").andExpect(status().isOk());

		flow.publicProfile(talent.id(), null).andExpect(status().isNotFound());
		flow.publicProfile(talent.id(), poster.header()).andExpect(status().isNotFound());
		flow.publicProfile(talent.id(), talent.header()).andExpect(status().isOk());
		api.applicants(poster, gig, "").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].profile.firstName").value("Asha"));
	}

	@Test
	void aThirdAccountCannotViewEitherContact() throws Exception {
		UUID application = WalkingSkeletonSupport.id(api.apply(talent, gig).andExpect(status().isCreated()));
		api.shortlist(poster, application, true).andExpect(status().isOk());
		api.consent(talent, application, true).andExpect(status().isOk());
		TestAccount third = flow.login();
		flow.name(third, "Rohan").andExpect(status().isOk());

		api.talentContact(third, application).andExpect(status().isNotFound());
		api.posterContact(third, application).andExpect(status().isNotFound());
		api.talentContact(null, application).andExpect(status().isUnauthorized());
		// the two sides themselves still can
		api.talentContact(poster, application).andExpect(status().isOk());
		api.posterContact(talent, application).andExpect(status().isOk());
	}

	@Test
	void cancellingAConfirmedHireNeedsAReasonAndThenReopensTheGigAndReleasesTheBooking() throws Exception {
		UUID application = WalkingSkeletonSupport.id(api.apply(talent, gig).andExpect(status().isCreated()));
		UUID hire = confirmedHire(application);
		assertThat(gigStatus()).isEqualTo("FILLED");
		assertThat(hasBooking()).isTrue();

		api.cancelHire(talent, hire, "{}").andExpect(status().isBadRequest());
		assertThat(gigStatus()).isEqualTo("FILLED");

		api.cancelHire(talent, hire, "{\"reason\":\"I'm ill\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.cancelledBy").value("TALENT"))
				.andExpect(jsonPath("$.cancelReason").value("I'm ill"));
		assertThat(gigStatus()).isEqualTo("OPEN");
		assertThat(hasBooking()).isFalse();
	}

	@Test
	void whenThePosterCancelsAFilledGigTheHireIsCancelledAndTheBookingReleased() throws Exception {
		UUID application = WalkingSkeletonSupport.id(api.apply(talent, gig).andExpect(status().isCreated()));
		UUID hire = confirmedHire(application);

		api.gigs.cancel(poster, gig, "{\"reason\":\"Venue changed\"}").andExpect(status().isOk());
		assertThat(gigStatus()).isEqualTo("CANCELLED");

		// the Hiring listener runs after the gig's own transaction
		WalkingSkeletonSupport.await("the hire is cancelled and the booking released", () -> {
			try {
				String status = JsonPath.read(WalkingSkeletonSupport.body(api.hire(talent, hire)), "$.status");
				return status.equals("CANCELLED") && !hasBooking();
			}
			catch (Exception ex) {
				return false;
			}
		});
		api.hire(talent, hire).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancelledBy").value("POSTER")).andExpect(jsonPath("$.cancelReason").value("Venue changed"));
	}

}
