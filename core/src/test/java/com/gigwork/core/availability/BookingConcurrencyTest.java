package com.gigwork.core.availability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.gigwork.core.identity.support.Concurrently;
import com.gigwork.core.identity.support.IdentityIntegrationTest;

/**
 * NFR-14: two overlapping bookings of one Talent can never both succeed, even for simultaneous requests. Repeated
 * many times, because one lucky run proves nothing; and a control shows the same race on a table without the
 * constraint, where every request succeeds.
 */
@IdentityIntegrationTest
class BookingConcurrencyTest {

	private static final int ROUNDS = 30;
	private static final int THREADS = 12;

	@Autowired
	Availability availability;
	@Autowired
	JdbcClient jdbc;

	private sealed interface Result permits Booked, Refused {
	}

	private record Booked(UUID id) implements Result {
	}

	private record Refused(Exception reason) implements Result {
	}

	private Result attempt(UUID account, UUID gig, Instant start, Instant end) {
		try {
			return new Booked(availability.book(account, gig, start, end));
		}
		catch (BookingConflictException | GigAlreadyBookedException e) {
			return new Refused(e);
		}
	}

	private int activeBookings(UUID account) {
		return jdbc.sql("SELECT count(*) FROM availability.booking WHERE account_id = :a AND status = 'ACTIVE'").param("a", account)
				.query(Integer.class).single();
	}

	@Test
	void manyParallelOverlappingBookingsOfOneTalentLeaveExactlyOne() throws Exception {
		Instant base = Instant.parse("2028-02-01T10:00:00Z");
		for (int round = 0; round < ROUNDS; round++) {
			UUID talent = UUID.randomUUID();
			List<Callable<Result>> tasks = new ArrayList<>();
			for (int i = 0; i < THREADS; i++) {
				// different gigs, every period overlapping all the others (they all contain base + 3 hours)
				Instant start = base.plusSeconds(60L * i);
				Instant end = base.plusSeconds(4 * 3600 + 60L * i);
				tasks.add(() -> attempt(talent, UUID.randomUUID(), start, end));
			}

			List<Result> results = Concurrently.runAll(tasks);

			long booked = results.stream().filter(r -> r instanceof Booked).count();
			assertThat(booked).as("round %d: successful bookings", round).isEqualTo(1);
			assertThat(results.stream().filter(r -> r instanceof Refused).map(r -> ((Refused) r).reason()))
					.as("round %d: the others are refused as conflicts", round).hasSize(THREADS - 1).allMatch(e -> e instanceof BookingConflictException);
			assertThat(activeBookings(talent)).isEqualTo(1);
		}
	}

	@Test
	void parallelBookingsOfDifferentTalentForTheSameTimeAllSucceed() throws Exception {
		Instant start = Instant.parse("2028-03-01T10:00:00Z");
		List<Callable<Result>> tasks = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			tasks.add(() -> attempt(UUID.randomUUID(), UUID.randomUUID(), start, start.plusSeconds(3600)));
		}

		assertThat(Concurrently.runAll(tasks)).allMatch(r -> r instanceof Booked);
	}

	@Test
	void parallelBackToBackBookingsAllSucceed() throws Exception {
		UUID talent = UUID.randomUUID();
		Instant start = Instant.parse("2028-04-01T00:00:00Z");
		List<Callable<Result>> tasks = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			Instant from = start.plusSeconds(3600L * i);
			tasks.add(() -> attempt(talent, UUID.randomUUID(), from, from.plusSeconds(3600)));
		}

		assertThat(Concurrently.runAll(tasks)).allMatch(r -> r instanceof Booked);
		assertThat(activeBookings(talent)).isEqualTo(THREADS);
	}

	@Test
	void parallelRepeatsOfTheSameBookingLeaveOneRowAndEveryCallerGetsItsId() throws Exception {
		for (int round = 0; round < 10; round++) {
			UUID talent = UUID.randomUUID();
			UUID gig = UUID.randomUUID();
			Instant start = Instant.parse("2028-05-01T10:00:00Z").plusSeconds(86400L * round);

			List<Result> results = Concurrently.runAll(8, () -> attempt(talent, gig, start, start.plusSeconds(3600)));

			Set<UUID> ids = new HashSet<>();
			results.forEach(r -> ids.add(((Booked) r).id())); // a repeat is never a conflict
			assertThat(ids).as("round %d: one booking for everyone", round).hasSize(1);
			assertThat(activeBookings(talent)).isEqualTo(1);
		}
	}

	@Test
	void parallelBookingsOfOneGigByDifferentTalentLeaveOneBooking() throws Exception {
		for (int round = 0; round < 10; round++) {
			UUID gig = UUID.randomUUID();
			Instant start = Instant.parse("2028-06-01T10:00:00Z").plusSeconds(86400L * round);

			List<Result> results = Concurrently.runAll(8, () -> attempt(UUID.randomUUID(), gig, start, start.plusSeconds(3600)));

			assertThat(results.stream().filter(r -> r instanceof Booked)).as("round %d", round).hasSize(1);
			assertThat(results.stream().filter(r -> r instanceof Refused).map(r -> ((Refused) r).reason()))
					.allMatch(e -> e instanceof GigAlreadyBookedException);
			assertThat(jdbc.sql("SELECT count(*) FROM availability.booking WHERE gig_id = :g AND status = 'ACTIVE'").param("g", gig).query(Integer.class).single()).isEqualTo(1);
		}
	}

	/**
	 * The control: the same insert, at the same moment, on a copy of the table with no constraints. Every request
	 * succeeds, so it is the exclusion constraint, and nothing else in the code, that makes the real test pass.
	 */
	@Test
	void withoutTheConstraintEveryOverlappingBookingSucceeds() throws Exception {
		jdbc.sql("CREATE TABLE availability.booking_control (LIKE availability.booking INCLUDING DEFAULTS)").update();
		try {
			UUID talent = UUID.randomUUID();
			Instant base = Instant.parse("2028-07-01T10:00:00Z");
			AtomicInteger counter = new AtomicInteger();

			List<Integer> inserted = Concurrently.runAll(THREADS, () -> {
				int i = counter.getAndIncrement();
				return jdbc.sql("""
						INSERT INTO availability.booking_control (id, account_id, gig_id, period, status, created_at)
						VALUES (gen_random_uuid(), :a, gen_random_uuid(),
						        tstzrange(CAST(:s AS timestamptz), CAST(:e AS timestamptz), '[)'), 'ACTIVE', now())""")
						.param("a", talent).param("s", java.time.OffsetDateTime.ofInstant(base.plusSeconds(60L * i), java.time.ZoneOffset.UTC))
						.param("e", java.time.OffsetDateTime.ofInstant(base.plusSeconds(4 * 3600 + 60L * i), java.time.ZoneOffset.UTC)).update();
			});

			assertThat(inserted).hasSize(THREADS).allMatch(n -> n == 1);
			assertThat(jdbc.sql("SELECT count(*) FROM availability.booking_control WHERE account_id = :a").param("a", talent).query(Integer.class).single())
					.isEqualTo(THREADS); // all overlapping: only the constraint prevents this in the real table
		}
		finally {
			jdbc.sql("DROP TABLE availability.booking_control").update();
		}
	}

}
