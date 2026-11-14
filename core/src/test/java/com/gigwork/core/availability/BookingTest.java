package com.gigwork.core.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.availability.ConflictingPeriod.Kind;
import com.gigwork.core.identity.support.IdentityIntegrationTest;

/** The booking guarantees of FR-29 / NFR-14, through the module's public API, against real PostgreSQL. */
@IdentityIntegrationTest
class BookingTest {

	@Autowired
	Availability availability;
	@Autowired
	JdbcClient jdbc;
	@Autowired
	TransactionTemplate transaction;

	private final UUID talent = UUID.randomUUID();

	private static Instant at(String text) {
		return Instant.parse(text);
	}

	private UUID book(UUID account, String start, String end) {
		return availability.book(account, UUID.randomUUID(), at(start), at(end));
	}

	private String status(UUID booking) {
		return jdbc.sql("SELECT status FROM availability.booking WHERE id = :id").param("id", booking).query(String.class).single();
	}

	// ---- overlaps -------------------------------------------------------------------------------------------------------

	@Test
	void backToBackBookingsDoNotOverlap() {
		UUID first = book(talent, "2027-01-10T10:00:00Z", "2027-01-10T14:00:00Z");
		UUID second = book(talent, "2027-01-10T14:00:00Z", "2027-01-10T18:00:00Z");

		assertThat(first).isNotEqualTo(second);
		assertThat(status(first)).isEqualTo("ACTIVE");
		assertThat(status(second)).isEqualTo("ACTIVE");
	}

	@Test
	void anyOverlapIsRefused() {
		book(talent, "2027-02-10T10:00:00Z", "2027-02-10T14:00:00Z");

		for (String[] other : new String[][] { { "2027-02-10T13:59:59Z", "2027-02-10T16:00:00Z" }, { "2027-02-10T08:00:00Z", "2027-02-10T10:00:00.000001Z" },
				{ "2027-02-10T11:00:00Z", "2027-02-10T12:00:00Z" }, { "2027-02-10T09:00:00Z", "2027-02-10T15:00:00Z" },
				{ "2027-02-10T10:00:00Z", "2027-02-10T14:00:00Z" } }) {
			assertThatThrownBy(() -> book(talent, other[0], other[1])).as(other[0] + ".." + other[1]).isInstanceOf(BookingConflictException.class);
		}
		book(talent, "2027-02-10T08:00:00Z", "2027-02-10T10:00:00Z"); // ends where the first starts
		book(UUID.randomUUID(), "2027-02-10T10:00:00Z", "2027-02-10T14:00:00Z"); // another Talent, same time
	}

	@Test
	void aCancelledBookingFreesTheTime() {
		UUID gig = UUID.randomUUID();
		availability.book(talent, gig, at("2027-03-10T10:00:00Z"), at("2027-03-10T14:00:00Z"));
		assertThatThrownBy(() -> book(talent, "2027-03-10T12:00:00Z", "2027-03-10T13:00:00Z")).isInstanceOf(BookingConflictException.class);

		assertThat(availability.release(gig)).isTrue();

		book(talent, "2027-03-10T12:00:00Z", "2027-03-10T13:00:00Z");
	}

	// ---- one active booking per gig; idempotency ------------------------------------------------------------------------

	@Test
	void aGigHasAtMostOneActiveBooking() {
		UUID gig = UUID.randomUUID();
		availability.book(talent, gig, at("2027-04-10T10:00:00Z"), at("2027-04-10T14:00:00Z"));

		assertThatThrownBy(() -> availability.book(UUID.randomUUID(), gig, at("2027-04-10T10:00:00Z"), at("2027-04-10T14:00:00Z")))
				.isInstanceOf(GigAlreadyBookedException.class);
		assertThatThrownBy(() -> availability.book(talent, gig, at("2027-04-11T10:00:00Z"), at("2027-04-11T14:00:00Z")))
				.isInstanceOf(GigAlreadyBookedException.class);

		availability.release(gig);
		availability.book(UUID.randomUUID(), gig, at("2027-04-10T10:00:00Z"), at("2027-04-10T14:00:00Z")); // free again
	}

	@Test
	void bookingTheSameThingAgainReturnsTheSameBooking() {
		UUID gig = UUID.randomUUID();
		UUID first = availability.book(talent, gig, at("2027-05-10T10:00:00Z"), at("2027-05-10T14:00:00Z"));

		UUID again = availability.book(talent, gig, at("2027-05-10T10:00:00Z"), at("2027-05-10T14:00:00Z"));

		assertThat(again).isEqualTo(first);
		assertThat(jdbc.sql("SELECT count(*) FROM availability.booking WHERE gig_id = :g").param("g", gig).query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void aCancelledBookingIsNotReturnedAsARepeat() {
		UUID gig = UUID.randomUUID();
		UUID first = availability.book(talent, gig, at("2027-06-10T10:00:00Z"), at("2027-06-10T14:00:00Z"));
		availability.release(gig);

		UUID second = availability.book(talent, gig, at("2027-06-10T10:00:00Z"), at("2027-06-10T14:00:00Z"));

		assertThat(second).isNotEqualTo(first);
		assertThat(status(first)).isEqualTo("CANCELLED");
		assertThat(status(second)).isEqualTo("ACTIVE");
	}

	@Test
	void releaseIsIdempotentAndSaysWhetherItCancelledAnything() {
		UUID gig = UUID.randomUUID();
		UUID booking = availability.book(talent, gig, at("2027-07-10T10:00:00Z"), at("2027-07-10T14:00:00Z"));

		assertThat(availability.release(gig)).isTrue();
		assertThat(availability.release(gig)).isFalse();
		assertThat(availability.release(UUID.randomUUID())).isFalse();
		assertThat(status(booking)).isEqualTo("CANCELLED");
		assertThat(jdbc.sql("SELECT cancelled_at IS NOT NULL FROM availability.booking WHERE id = :id").param("id", booking).query(Boolean.class).single()).isTrue();
	}

	// ---- inside the caller's transaction -------------------------------------------------------------------------------

	@Test
	void aRefusedBookingDoesNotSpoilTheCallersTransaction() {
		UUID mine = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		book(talent, "2027-08-10T10:00:00Z", "2027-08-10T14:00:00Z");

		transaction.executeWithoutResult(status -> {
			availability.book(talent, mine, at("2027-08-11T10:00:00Z"), at("2027-08-11T14:00:00Z"));
			assertThatThrownBy(() -> book(talent, "2027-08-10T12:00:00Z", "2027-08-10T13:00:00Z")).isInstanceOf(BookingConflictException.class);
			assertThatThrownBy(() -> availability.book(talent, mine, at("2027-08-12T10:00:00Z"), at("2027-08-12T14:00:00Z")))
					.isInstanceOf(GigAlreadyBookedException.class);
			// the transaction is still usable: a repeat, a new booking and a query all work
			assertThat(availability.book(talent, mine, at("2027-08-11T10:00:00Z"), at("2027-08-11T14:00:00Z"))).isNotNull();
			availability.book(talent, other, at("2027-08-13T10:00:00Z"), at("2027-08-13T14:00:00Z"));
			assertThat(availability.conflicts(talent, at("2027-08-10T00:00:00Z"), at("2027-08-14T00:00:00Z")).periods()).hasSize(3);
		});

		assertThat(jdbc.sql("SELECT count(*) FROM availability.booking WHERE gig_id IN (:a, :b)").param("a", mine).param("b", other).query(Integer.class).single()).isEqualTo(2);
	}

	// ---- conflicts() for the apply warning -----------------------------------------------------------------------------

	@Test
	void conflictsListsOnlyTheOverlappingPeriodsWithTheirKind() {
		insertBusyBlock(talent, "2027-09-10T09:00:00Z", "2027-09-10T11:00:00Z");
		book(talent, "2027-09-10T12:00:00Z", "2027-09-10T15:00:00Z");
		insertBusyBlock(talent, "2027-09-20T09:00:00Z", "2027-09-20T11:00:00Z"); // elsewhere in time

		Conflicts conflicts = availability.conflicts(talent, at("2027-09-10T10:00:00Z"), at("2027-09-10T13:00:00Z"));

		assertThat(conflicts.hasConflict()).isTrue();
		assertThat(conflicts.periods()).containsExactly(
				new ConflictingPeriod(at("2027-09-10T09:00:00Z"), at("2027-09-10T11:00:00Z"), Kind.BUSY_BLOCK),
				new ConflictingPeriod(at("2027-09-10T12:00:00Z"), at("2027-09-10T15:00:00Z"), Kind.BOOKING));
	}

	@Test
	void conflictsAreHalfOpenAndIgnoreCancelledBookingsAndOtherTalent() {
		UUID gig = UUID.randomUUID();
		availability.book(talent, gig, at("2027-10-10T10:00:00Z"), at("2027-10-10T14:00:00Z"));
		insertBusyBlock(UUID.randomUUID(), "2027-10-10T10:00:00Z", "2027-10-10T14:00:00Z");

		assertThat(availability.conflicts(talent, at("2027-10-10T14:00:00Z"), at("2027-10-10T16:00:00Z")).hasConflict()).isFalse(); // starts as it ends
		assertThat(availability.conflicts(talent, at("2027-10-10T08:00:00Z"), at("2027-10-10T10:00:00Z")).hasConflict()).isFalse();
		assertThat(availability.conflicts(talent, at("2027-10-10T13:00:00Z"), at("2027-10-10T16:00:00Z")).hasConflict()).isTrue();
		availability.release(gig);
		Conflicts none = availability.conflicts(talent, at("2027-10-10T00:00:00Z"), at("2027-10-11T00:00:00Z"));
		assertThat(none.hasConflict()).isFalse();
		assertThat(none.periods()).isEmpty();
	}

	@Test
	void conflictsRevealNoIdsOrDetails() {
		insertBusyBlock(talent, "2027-11-10T09:00:00Z", "2027-11-10T11:00:00Z");

		Conflicts conflicts = availability.conflicts(talent, at("2027-11-10T00:00:00Z"), at("2027-11-11T00:00:00Z"));

		assertThat(conflicts.toString()).doesNotContain(talent.toString());
		assertThat(ConflictingPeriod.class.getRecordComponents()).extracting(c -> c.getName()).containsExactly("start", "end", "kind");
		assertThat(Conflicts.class.getRecordComponents()).extracting(c -> c.getName()).containsExactly("hasConflict", "periods");
	}

	// ---- input ---------------------------------------------------------------------------------------------------------

	@Test
	void aPeriodNeedsAnEndAfterTheStart() {
		assertThatThrownBy(() -> availability.book(talent, UUID.randomUUID(), at("2027-12-10T10:00:00Z"), at("2027-12-10T10:00:00Z"))).isInstanceOf(InvalidPeriodException.class);
		assertThatThrownBy(() -> availability.book(talent, UUID.randomUUID(), at("2027-12-10T10:00:00Z"), at("2027-12-10T09:00:00Z"))).isInstanceOf(InvalidPeriodException.class);
		assertThatThrownBy(() -> availability.book(talent, UUID.randomUUID(), null, at("2027-12-10T09:00:00Z"))).isInstanceOf(InvalidPeriodException.class);
		assertThatThrownBy(() -> availability.conflicts(talent, at("2027-12-10T10:00:00Z"), at("2027-12-10T10:00:00Z"))).isInstanceOf(InvalidPeriodException.class);
		// below a microsecond they are the same instant
		assertThatThrownBy(() -> availability.book(talent, UUID.randomUUID(), at("2027-12-10T10:00:00.000000100Z"), at("2027-12-10T10:00:00.000000900Z")))
				.isInstanceOf(InvalidPeriodException.class);
	}

	@Test
	void timesAreStoredInWholeMicroseconds() {
		UUID booking = availability.book(talent, UUID.randomUUID(), at("2028-01-10T10:00:00.123456789Z"), at("2028-01-10T11:00:00.987654321Z"));

		var stored = jdbc.sql("SELECT lower(period) AS s, upper(period) AS e FROM availability.booking WHERE id = :id").param("id", booking)
				.query((rs, n) -> new Instant[] { rs.getObject("s", OffsetDateTime.class).toInstant(), rs.getObject("e", OffsetDateTime.class).toInstant() }).single();
		assertThat(stored[0]).isEqualTo(at("2028-01-10T10:00:00.123456Z"));
		assertThat(stored[1]).isEqualTo(at("2028-01-10T11:00:00.987654Z"));
	}

	private void insertBusyBlock(UUID account, String start, String end) {
		jdbc.sql("""
				INSERT INTO availability.busy_block (id, account_id, period, all_day, created_at)
				VALUES (gen_random_uuid(), :a, tstzrange(CAST(:s AS timestamptz), CAST(:e AS timestamptz), '[)'), false, now())""")
				.param("a", account).param("s", OffsetDateTime.parse(start)).param("e", OffsetDateTime.parse(end)).update();
	}

}
