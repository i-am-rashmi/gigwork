package com.gigwork.core.availability.application;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.availability.Availability;
import com.gigwork.core.availability.BookingConflictException;
import com.gigwork.core.availability.Conflicts;
import com.gigwork.core.availability.GigAlreadyBookedException;
import com.gigwork.core.availability.InvalidPeriodException;
import com.gigwork.core.availability.persistence.BookingStore;

/**
 * Bookings: the times of confirmed hires (FR-29, NFR-14). The database decides, never code that checks first: the
 * INSERT either succeeds or is refused by the exclusion constraint (SQLState 23P01) or the one-active-booking-per-gig
 * index (23505).
 *
 * <p>The INSERT runs behind a JDBC savepoint. PostgreSQL aborts a whole transaction after a failed statement, so
 * without one a refused booking would ruin the caller's transaction (Hiring's), even though a refusal is an
 * expected answer; with it, only the INSERT is undone and the caller can carry on, for example to look up the
 * booking that makes this call a harmless repeat. (Spring's JPA transaction manager cannot do nested transactions
 * with Hibernate, so the savepoint is taken on the transaction's JDBC connection directly.) The exception is thrown
 * after the transaction block has finished, so a refusal does not by itself mark a caller's transaction rollback-only.
 */
@Service
class BookingService implements Availability {

	/** What happened to the INSERT: the booking's ID, or why the database refused it. */
	private record Outcome(UUID bookingId, String sqlState, String message, RuntimeException refusal) {

		static Outcome booked(UUID id) {
			return new Outcome(id, null, null, null);
		}

		static Outcome refused(RuntimeException refusal) {
			SQLException cause = sqlException(refusal);
			return new Outcome(null, cause == null ? null : cause.getSQLState(), cause == null ? null : cause.getMessage(), refusal);
		}

	}

	private static final int MAX_DEADLOCK_RETRIES = 3;

	private final BookingStore bookings;
	private final TransactionTemplate transaction;
	private final DataSource dataSource;
	private final Clock clock;

	BookingService(BookingStore bookings, PlatformTransactionManager transactions, DataSource dataSource, Clock clock) {
		this.bookings = bookings;
		this.transaction = new TransactionTemplate(transactions);
		this.dataSource = dataSource;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public Conflicts conflicts(UUID accountId, Instant start, Instant end) {
		Instant from = micros(start);
		Instant to = micros(end);
		requireOrdered(from, to);
		return Conflicts.of(bookings.overlappingPeriods(accountId, from, to));
	}

	@Override
	public UUID book(UUID accountId, UUID gigId, Instant start, Instant end) {
		Instant from = micros(start);
		Instant to = micros(end);
		requireOrdered(from, to);
		UUID id = UUID.randomUUID();
		Outcome outcome = transaction.execute(status -> insertBehindSavepoint(id, accountId, gigId, from, to));
		if (outcome.bookingId() != null) {
			return outcome.bookingId();
		}
		if (BookingStore.OVERLAP_STATE.equals(outcome.sqlState())) {
			throw new BookingConflictException(outcome.refusal());
		}
		if (BookingStore.UNIQUE_STATE.equals(outcome.sqlState()) && outcome.message() != null
				&& outcome.message().contains(BookingStore.ONE_ACTIVE_PER_GIG)) {
			throw new GigAlreadyBookedException(outcome.refusal());
		}
		throw outcome.refusal(); // still deadlocking after the retries, or an unexpected database error
	}

	/**
	 * The Talent's bookings take turns (an advisory lock), which avoids the deadlocks that simultaneous overlapping
	 * inserts would otherwise provoke. If a deadlock happens anyway (another lock in the caller's transaction) it is not
	 * a failure of the booking: once the winner has committed the same INSERT is refused as an overlap, so it is simply
	 * tried again behind a fresh savepoint.
	 */
	private Outcome insertBehindSavepoint(UUID id, UUID accountId, UUID gigId, Instant from, Instant to) {
		Connection connection = DataSourceUtils.getConnection(dataSource); // the connection of the current transaction
		try {
			bookings.lockTalent(accountId); // take turns with this Talent's other bookings (see BookingStore.lockTalent)
			PessimisticLockingFailureException lastDeadlock = null;
			for (int attempt = 1; attempt <= MAX_DEADLOCK_RETRIES + 1; attempt++) {
				Savepoint savepoint = connection.setSavepoint();
				try {
					bookings.insert(id, accountId, gigId, from, to, clock.instant());
					connection.releaseSavepoint(savepoint);
					return Outcome.booked(id);
				}
				catch (DataIntegrityViolationException refused) {
					connection.rollback(savepoint); // only the INSERT is undone
					Outcome outcome = Outcome.refused(refused);
					boolean constraint = BookingStore.OVERLAP_STATE.equals(outcome.sqlState()) || BookingStore.UNIQUE_STATE.equals(outcome.sqlState());
					// a repeat of the same booking is not a conflict: return the one that exists
					return constraint ? bookings.findActive(accountId, gigId, from, to).map(Outcome::booked).orElse(outcome) : outcome;
				}
				catch (PessimisticLockingFailureException deadlock) {
					connection.rollback(savepoint);
					lastDeadlock = deadlock;
				}
			}
			return Outcome.refused(lastDeadlock);
		}
		catch (SQLException e) {
			throw new DataAccessResourceFailureException("could not use a savepoint for the booking", e);
		}
		finally {
			DataSourceUtils.releaseConnection(connection, dataSource);
		}
	}

	@Override
	@Transactional
	public boolean release(UUID gigId) {
		return bookings.cancelActive(gigId, clock.instant());
	}

	private static Instant micros(Instant instant) {
		if (instant == null) {
			throw new InvalidPeriodException();
		}
		return instant.truncatedTo(ChronoUnit.MICROS);
	}

	private static void requireOrdered(Instant from, Instant to) {
		if (!to.isAfter(from)) {
			throw new InvalidPeriodException();
		}
	}

	static SQLException sqlException(Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause()) {
			if (t instanceof SQLException sql) {
				return sql;
			}
		}
		return null;
	}

}
