package com.gigwork.core.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

/** Withdrawing an application, and applying again afterwards (FR-23, NFR-39). */
@IdentityIntegrationTest
@org.springframework.context.annotation.Import(WithdrawApiTest.Events.class)
class WithdrawApiTest {

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
	EventLog events;

	HiringApiSupport api;
	TestAccount poster;
	TestAccount talent;
	UUID gig;

	@org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
	static class Events {

		@org.springframework.context.annotation.Bean
		EventLog eventLog() {
			return new EventLog();
		}

	}

	/** Collects the events Hiring publishes. */
	static class EventLog {

		final java.util.List<Object> all = new java.util.concurrent.CopyOnWriteArrayList<>();

		@org.springframework.context.event.EventListener
		void on(ApplicationSubmitted e) {
			all.add(e);
		}

		@org.springframework.context.event.EventListener
		void on(ApplicationWithdrawn e) {
			all.add(e);
		}

	}

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		api = new HiringApiSupport(mvc, reference, accounts, clock);
		poster = accounts.create();
		talent = api.talent("Asha", "photographer");
		gig = api.openGig(poster);
		events.all.clear();
	}

	private String statusOf(UUID application) {
		return jdbc.sql("SELECT status FROM hiring.application WHERE id = :id").param("id", application).query(String.class).single();
	}

	private int submissions() {
		return jdbc.sql("SELECT count(*) FROM hiring.application_submission WHERE talent_id = :t").param("t", talent.id())
				.query(Integer.class).single();
	}

	@Test
	void theApplicantWithdrawsAndTheEventIsPublished() throws Exception {
		UUID application = api.applyOk(talent, gig);

		api.withdraw(talent, application).andExpect(status().isNoContent());

		assertThat(statusOf(application)).isEqualTo("WITHDRAWN");
		assertThat(jdbc.sql("SELECT withdrawn_at IS NOT NULL FROM hiring.application WHERE id = :id").param("id", application)
				.query(Boolean.class).single()).isTrue();
		assertThat(events.all).filteredOn(ApplicationWithdrawn.class::isInstance).singleElement().satisfies(e -> {
			ApplicationWithdrawn w = (ApplicationWithdrawn) e;
			assertThat(w.applicationId()).isEqualTo(application);
			assertThat(w.gigId()).isEqualTo(gig);
			assertThat(w.talentId()).isEqualTo(talent.id());
			assertThat(w.posterId()).isEqualTo(poster.id());
		});
	}

	@Test
	void withdrawingTwiceIsRefusedAndPublishesNothingTheSecondTime() throws Exception {
		UUID application = api.applyOk(talent, gig);
		api.withdraw(talent, application).andExpect(status().isNoContent());
		events.all.clear();

		api.withdraw(talent, application).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_WITHDRAWN"));
		assertThat(events.all).isEmpty();
	}

	@Test
	void anotherTalentOrAnUnknownApplicationIsNotFoundAndNothingChanges() throws Exception {
		UUID application = api.applyOk(talent, gig);
		TestAccount other = api.talent("Bela", "photographer");

		api.withdraw(other, application).andExpect(status().isNotFound());
		api.withdraw(poster, application).andExpect(status().isNotFound()); // not even the gig's Poster
		api.withdraw(talent, UUID.randomUUID()).andExpect(status().isNotFound());

		assertThat(statusOf(application)).isEqualTo("ACTIVE");
	}

	@Test
	void aVisitorCannotWithdraw() throws Exception {
		api.withdraw(null, UUID.randomUUID()).andExpect(status().isUnauthorized());
	}

	@Test
	void applyingAgainAfterWithdrawingReactivatesTheSameApplicationAndPublishesAReapply() throws Exception {
		UUID application = api.applyOk(talent, gig);
		api.withdraw(talent, application).andExpect(status().isNoContent());

		UUID again = HiringApiSupport.id(api.apply(talent, gig, "{\"note\":\"I'm back\"}").andExpect(status().isCreated()));

		assertThat(again).isEqualTo(application);
		assertThat(statusOf(application)).isEqualTo("ACTIVE");
		assertThat(submissions()).isEqualTo(2);
		assertThat(events.all).filteredOn(ApplicationSubmitted.class::isInstance).extracting(e -> ((ApplicationSubmitted) e).reapply())
				.containsExactly(false, true);
	}

	@Test
	void applyAndWithdrawLoopsStillCountTowardsTheLimit() throws Exception {
		for (int i = 0; i < 30; i++) {
			UUID application = HiringApiSupport.id(api.apply(talent, gig).andExpect(status().isCreated()));
			api.withdraw(talent, application).andExpect(status().isNoContent());
		}
		api.apply(talent, gig).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("APPLY_LIMIT_REACHED"));
		assertThat(submissions()).isEqualTo(30);
		assertThat(jdbc.sql("SELECT count(*) FROM hiring.application WHERE talent_id = :t").param("t", talent.id())
				.query(Integer.class).single()).isEqualTo(1); // still one row
	}

}
