package com.gigwork.core.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.gigwork.core.availability.application.AddBusyBlock;
import com.gigwork.core.availability.application.BusyBlockService;
import com.gigwork.core.availability.domain.BusyBlockLimitException;
import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.profile.ProfileApiSupport;
import com.gigwork.core.reference.ReferenceData;

/** FR-7: at most 200 upcoming busy blocks per Talent, even when the adds arrive at the same moment. */
@IdentityIntegrationTest
class BusyBlockLimitTest {

	private static final Instant NOW = Instant.parse("2026-11-14T06:30:00Z");
	private static final int LIMIT = 200;

	@Autowired
	BusyBlockService service;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	MutableClock clock;
	@Autowired
	TestAccounts accounts;
	@Autowired
	ReferenceData reference;
	@Autowired
	MockMvc mvc;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	private TestAccount talentWithProfile() throws Exception {
		TestAccount talent = accounts.create();
		ProfileApiSupport api = new ProfileApiSupport(mvc, reference);
		api.put(talent, "work", api.simpleWork(api.categoryId("photographer"))).andExpect(status().isOk());
		return talent;
	}

	private void prefill(UUID account, int count, String start) {
		Instant first = Instant.parse(start);
		for (int i = 0; i < count; i++) {
			Instant from = first.plusSeconds(3600L * i);
			jdbc.sql("""
					INSERT INTO availability.busy_block (id, account_id, period, all_day, created_at)
					VALUES (gen_random_uuid(), :a, tstzrange(CAST(:s AS timestamptz), CAST(:e AS timestamptz), '[)'), false, now())""")
					.param("a", account).param("s", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
					.param("e", OffsetDateTime.ofInstant(from.plusSeconds(1800), ZoneOffset.UTC)).update();
		}
	}

	private int blocks(UUID account) {
		return jdbc.sql("SELECT count(*) FROM availability.busy_block WHERE account_id = :a").param("a", account).query(Integer.class).single();
	}

	private static AddBusyBlock block(int i) {
		OffsetDateTime start = OffsetDateTime.parse("2027-06-01T00:00:00Z").plusHours(i);
		return new AddBusyBlock(start, start.plusMinutes(30), null, null, null);
	}

	@Test
	void theTwoHundredAndFirstBlockIsRefused() throws Exception {
		TestAccount talent = talentWithProfile();
		prefill(talent.id(), LIMIT - 1, "2027-01-01T00:00:00Z");

		service.add(talent.id(), block(0)); // the 200th
		assertThatThrownBy(() -> service.add(talent.id(), block(1))).isInstanceOf(BusyBlockLimitException.class)
				.hasMessageContaining("at most 200");

		assertThat(blocks(talent.id())).isEqualTo(LIMIT);
	}

	@Test
	void theApiAnswers400WithAClearMessage() throws Exception {
		TestAccount talent = talentWithProfile();
		prefill(talent.id(), LIMIT, "2027-01-01T00:00:00Z");

		mvc.perform(MockMvcRequestBuilders.post("/v1/me/busy-blocks").header("Authorization", talent.header()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"start\":\"2027-08-01T10:00:00Z\",\"end\":\"2027-08-01T11:00:00Z\"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("You can have at most 200 upcoming busy blocks. Remove one to add another."));
	}

	@Test
	void blocksThatHaveEndedDoNotCount() throws Exception {
		TestAccount talent = talentWithProfile();
		prefill(talent.id(), LIMIT, "2026-01-01T00:00:00Z"); // all over by November 2026

		service.add(talent.id(), block(0));

		assertThat(blocks(talent.id())).isEqualTo(LIMIT + 1);
	}

	@Test
	void removingABlockMakesRoom() throws Exception {
		TestAccount talent = talentWithProfile();
		prefill(talent.id(), LIMIT, "2027-01-01T00:00:00Z");
		assertThatThrownBy(() -> service.add(talent.id(), block(0))).isInstanceOf(BusyBlockLimitException.class);
		UUID one = jdbc.sql("SELECT id FROM availability.busy_block WHERE account_id = :a LIMIT 1").param("a", talent.id()).query(UUID.class).single();

		assertThat(service.remove(talent.id(), one)).isTrue();

		service.add(talent.id(), block(0));
	}

	@Test
	void parallelAddsNeverGetPastTheLimit() throws Exception {
		for (int round = 0; round < 5; round++) {
			TestAccount talent = talentWithProfile();
			prefill(talent.id(), LIMIT - 10, "2027-01-01T00:00:00Z");
			List<Callable<Boolean>> tasks = new ArrayList<>();
			for (int i = 0; i < 30; i++) {
				AddBusyBlock request = block(i);
				tasks.add(() -> {
					try {
						service.add(talent.id(), request);
						return true;
					}
					catch (BusyBlockLimitException e) {
						return false;
					}
				});
			}

			List<Boolean> results = Concurrently.runAll(tasks);

			assertThat(results.stream().filter(ok -> ok).count()).as("round %d: adds that fit", round).isEqualTo(10);
			assertThat(blocks(talent.id())).as("round %d", round).isEqualTo(LIMIT);
		}
	}

	@Test
	void theLimitIsPerTalent() throws Exception {
		TestAccount full = talentWithProfile();
		TestAccount other = talentWithProfile();
		prefill(full.id(), LIMIT, "2027-01-01T00:00:00Z");

		service.add(other.id(), block(0));

		assertThat(blocks(other.id())).isEqualTo(1);
	}

}
