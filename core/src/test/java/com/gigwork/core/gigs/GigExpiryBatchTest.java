package com.gigwork.core.gigs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import com.gigwork.core.gigs.application.GigExpiry;
import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;

/**
 * Sweeps in batches and in parallel (ADR-0010): however many runs start together, each due gig is expired once and
 * has exactly one GigExpired event. The batch is 25, so a sweep of 200 gigs takes many transactions that interleave.
 */
@IdentityIntegrationTest
@Import(EventCollector.class)
@TestPropertySource(properties = "gigwork.gigs.expiry-batch-size=25")
class GigExpiryBatchTest {

	private static final UUID POSTER = UUID.randomUUID();

	@Autowired
	MutableClock clock;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	GigExpiry expiry;
	@Autowired
	EventCollector collected;

	@BeforeEach
	void setUp() {
		clock.reset();
		jdbc.sql("DELETE FROM gigs.gig").update();
		collected.clear();
	}

	@AfterEach
	void tearDown() {
		jdbc.sql("DELETE FROM gigs.gig").update();
	}

	/** Open gigs that started between 1 and {@code count} minutes ago, a few still in the future, a few cancelled. */
	private void due(int count) {
		jdbc.sql("""
				INSERT INTO gigs.gig (id, poster_id, category_id, title, description, start_at, end_at, work_mode, status,
				    published_at, updated_at)
				SELECT gen_random_uuid(), :poster, gen_random_uuid(), 't', 'd', now() - n * interval '1 minute',
				    now() - n * interval '1 minute' + interval '2 hours', 'REMOTE', 'OPEN', now() - interval '1 day', now()
				FROM generate_series(1, :count) n""").param("poster", POSTER).param("count", count).update();
		jdbc.sql("""
				INSERT INTO gigs.gig (id, poster_id, category_id, title, description, start_at, end_at, work_mode, status,
				    published_at, updated_at)
				SELECT gen_random_uuid(), :poster, gen_random_uuid(), 't', 'd', now() + interval '3 days',
				    now() + interval '4 days', 'REMOTE', 'OPEN', now(), now()
				FROM generate_series(1, 5) n""").param("poster", POSTER).update();
		jdbc.sql("""
				INSERT INTO gigs.gig (id, poster_id, category_id, title, description, start_at, end_at, work_mode, status,
				    published_at, updated_at, closed_at)
				SELECT gen_random_uuid(), :poster, gen_random_uuid(), 't', 'd', now() - interval '3 hours',
				    now() - interval '1 hour', 'REMOTE', 'CANCELLED', now() - interval '1 day', now(), now()
				FROM generate_series(1, 5) n""").param("poster", POSTER).update();
		clock.set(java.time.Instant.now().plusSeconds(60)); // the database's now() is slightly before the clock's
	}

	private long count(String status) {
		return jdbc.sql("SELECT count(*) FROM gigs.gig WHERE status = :s").param("s", status).query(Long.class).single();
	}

	@Test
	void aSweepLargerThanOneBatchExpiresEveryDueGigOnce() {
		due(130);

		assertThat(expiry.run()).isEqualTo(130);

		assertThat(count("EXPIRED")).isEqualTo(130);
		assertThat(count("OPEN")).isEqualTo(5);
		assertThat(count("CANCELLED")).isEqualTo(5);
		assertThat(collected.of(GigExpired.class)).hasSize(130);
		assertThat(collected.of(GigExpired.class).stream().map(GigExpired::gigId).distinct()).hasSize(130);
	}

	@Test
	void aSweepOfExactlyOneBatchEndsCleanly() {
		due(25);

		assertThat(expiry.run()).isEqualTo(25);
		assertThat(expiry.run()).isZero();
	}

	@Test
	void theRunTwiceInParallelExpiresEachGigOnceAndWritesOneEventForEach() throws Exception {
		for (int round = 0; round < 5; round++) {
			setUp();
			due(200);

			List<Long> changed = Concurrently.runAll(2, () -> expiry.run());

			assertThat(changed.stream().mapToLong(Long::longValue).sum()).as("rows changed by both runs").isEqualTo(200);
			assertThat(count("EXPIRED")).isEqualTo(200);
			assertThat(collected.of(GigExpired.class)).hasSize(200);
			assertThat(collected.of(GigExpired.class).stream().map(GigExpired::gigId).distinct()).hasSize(200);
		}
	}

	@Test
	void manyRunsAtOnceStillExpireEachGigOnce() throws Exception {
		due(200);
		List<Callable<Long>> runs = java.util.stream.IntStream.range(0, 8).<Callable<Long>>mapToObj(i -> () -> expiry.run()).toList();

		List<Long> changed = Concurrently.runAll(runs);

		assertThat(changed.stream().mapToLong(Long::longValue).sum()).isEqualTo(200);
		assertThat(collected.of(GigExpired.class).stream().map(GigExpired::gigId).distinct()).hasSize(200);
		assertThat(collected.of(GigExpired.class)).hasSize(200);
	}

}
