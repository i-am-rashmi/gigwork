package com.gigwork.core.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.hiring.HiringApiSupport.Shortlisted;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.SqlCounter;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/** The hire of an application in the Poster's applicant list and in the Talent's applications. */
@IdentityIntegrationTest
class HireListsApiTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	ReferenceData reference;
	@Autowired
	TestAccounts accounts;
	@Autowired
	MutableClock clock;

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
	}

	@Test
	void anApplicationWithoutAHireHasNone() throws Exception {
		api.applicants(poster, gig, "").andExpect(jsonPath("$.items[0].hire").doesNotExist());
		api.mine(talent, "").andExpect(jsonPath("$.items[0].hire").doesNotExist());
	}

	@Test
	void bothSidesSeeThePendingHireWithItsIdSoTheOtherSideCanAnswerIt() throws Exception {
		UUID hire = HiringApiSupport.id(api.startHire(poster, application).andExpect(status().isCreated()));

		api.mine(talent, "").andExpect(jsonPath("$.items[0].hire.id").value(hire.toString())).andExpect(jsonPath("$.items[0].hire.status").value("PENDING"))
				.andExpect(jsonPath("$.items[0].hire.startedBy").value("POSTER")).andExpect(jsonPath("$.items[0].hire.expiresAt").exists());
		api.applicants(poster, gig, "").andExpect(jsonPath("$.items[0].hire.id").value(hire.toString()))
				.andExpect(jsonPath("$.items[0].hire.status").value("PENDING"));
		api.confirmHire(talent, hire).andExpect(status().isOk());
		api.mine(talent, "").andExpect(jsonPath("$.items[0].hire.status").value("CONFIRMED"));
		api.applicants(poster, gig, "").andExpect(jsonPath("$.items[0].hire.status").value("CONFIRMED"));
	}

	@Test
	void theLatestHireOfTheApplicationIsShownWhateverBecameOfEarlierOnes() throws Exception {
		UUID first = HiringApiSupport.id(api.startHire(talent, application).andExpect(status().isCreated()));
		api.declineHire(poster, first).andExpect(status().isOk());
		api.mine(talent, "").andExpect(jsonPath("$.items[0].hire.status").value("DECLINED"));

		clock.advance(Duration.ofSeconds(1));
		UUID second = HiringApiSupport.id(api.startHire(poster, application).andExpect(status().isCreated()));

		api.mine(talent, "").andExpect(jsonPath("$.items[0].hire.id").value(second.toString())).andExpect(jsonPath("$.items[0].hire.status").value("PENDING"));
		api.applicants(poster, gig, "").andExpect(jsonPath("$.items[0].hire.id").value(second.toString()));
	}

	@Test
	void theHireOfOneApplicantIsNotShownOnAnother() throws Exception {
		Shortlisted other = api.shortlisted(poster, gig, "Bela");
		api.startHire(poster, application).andExpect(status().isCreated());

		api.mine(other.talent(), "").andExpect(jsonPath("$.items[0].hire").doesNotExist());
		String json = api.applicants(poster, gig, "").andReturn().getResponse().getContentAsString();
		assertThat(com.jayway.jsonpath.JsonPath.<List<Object>>read(json, "$.items[?(@.hire)]")).hasSize(1);
	}

	@Test
	void theListsStillUseAFixedNumberOfStatementsWithHires() throws Exception {
		List<UUID> gigs = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			UUID g = api.openGig(poster);
			api.applyOk(talent, g);
			gigs.add(g);
			clock.advance(Duration.ofSeconds(1));
		}
		for (UUID g : gigs) {
			UUID app = jdbcApplication(g);
			api.shortlist(poster, app, false).andExpect(status().isOk());
			api.startHire(poster, app).andExpect(status().isCreated());
		}
		api.mine(talent, "?size=2");
		long two = SqlCounter.during(() -> call(() -> api.mine(talent, "?size=2")));
		long ten = SqlCounter.during(() -> call(() -> api.mine(talent, "?size=10")));
		assertThat(two).isPositive();
		assertThat(ten).isEqualTo(two);
	}

	@Autowired
	org.springframework.jdbc.core.simple.JdbcClient jdbc;

	private UUID jdbcApplication(UUID g) {
		return jdbc.sql("SELECT id FROM hiring.application WHERE gig_id = :g AND talent_id = :t").param("g", g).param("t", talent.id())
				.query(UUID.class).single();
	}

	private interface Call {

		void run() throws Exception;

	}

	private static void call(Call call) {
		try {
			call.run();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
