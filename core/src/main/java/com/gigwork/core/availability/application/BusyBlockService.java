package com.gigwork.core.availability.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.availability.application.TalentZones.TalentZone;
import com.gigwork.core.availability.config.AvailabilityProperties;
import com.gigwork.core.availability.domain.BusyBlockLimitException;
import com.gigwork.core.availability.domain.BusyBlockRules;
import com.gigwork.core.availability.domain.BusyBlockRules.NewBlock;
import com.gigwork.core.availability.domain.InvalidBusyBlockException;
import com.gigwork.core.availability.domain.ProfileRequiredException;
import com.gigwork.core.availability.persistence.BookingStore;
import com.gigwork.core.availability.persistence.BusyBlockStore;
import com.gigwork.core.availability.persistence.CalendarEntry;

/** A Talent's busy blocks and calendar (FR-7). Private: every method is about the caller's own account. */
@Service
public class BusyBlockService {

	private final BusyBlockStore blocks;
	private final BookingStore bookings;
	private final TalentZones zones;
	private final AvailabilityProperties properties;
	private final TransactionTemplate transaction;
	private final Clock clock;

	BusyBlockService(BusyBlockStore blocks, BookingStore bookings, TalentZones zones, AvailabilityProperties properties,
			TransactionTemplate transaction, Clock clock) {
		this.blocks = blocks;
		this.bookings = bookings;
		this.zones = zones;
		this.properties = properties;
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * Adds a block. The rules are checked first; then one transaction takes the Talent's lock, counts the blocks that
	 * have not ended and inserts, so simultaneous adds cannot get past the limit.
	 */
	public CalendarEntry add(UUID accountId, AddBusyBlock request) {
		TalentZone talent = zones.of(accountId);
		if (!talent.hasProfile()) {
			throw new ProfileRequiredException();
		}
		Instant now = clock.instant();
		NewBlock block = rules(request, talent.zone(), now);
		UUID id = UUID.randomUUID();
		transaction.executeWithoutResult(status -> {
			blocks.lockTalent(accountId);
			if (blocks.countUpcoming(accountId, now) >= properties.maxUpcomingBlocks()) {
				throw new BusyBlockLimitException(properties.maxUpcomingBlocks());
			}
			blocks.insert(id, accountId, block.start(), block.end(), block.allDay(), block.firstDate(), block.lastDate(), now);
		});
		return blocks.find(id, accountId);
	}

	/** True if the account's block was removed; false for an unknown block or someone else's (never tell which). */
	public boolean remove(UUID accountId, UUID blockId) {
		return blocks.delete(blockId, accountId);
	}

	/**
	 * The Talent's blocks and active bookings overlapping the dates {@code from} to {@code to} (both inclusive), read
	 * in the Talent's city zone, sorted by start. At most {@code maxCalendarDays} days.
	 */
	public CalendarView calendar(UUID accountId, LocalDate from, LocalDate to) {
		if (from == null || to == null) {
			throw new InvalidBusyBlockException("Give both from and to as dates (YYYY-MM-DD).");
		}
		if (to.isBefore(from)) {
			throw new InvalidBusyBlockException("'to' can't be before 'from'.");
		}
		if (ChronoUnit.DAYS.between(from, to) + 1 > properties.maxCalendarDays()) {
			throw new InvalidBusyBlockException("A calendar request can cover at most " + properties.maxCalendarDays() + " days.");
		}
		ZoneId zone = zones.of(accountId).zone();
		Instant start = from.atStartOfDay(zone).toInstant();
		Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
		List<CalendarEntry> entries = new ArrayList<>(blocks.overlapping(accountId, start, end));
		entries.addAll(bookings.activeOverlapping(accountId, start, end));
		entries.sort(Comparator.comparing(CalendarEntry::start).thenComparing(CalendarEntry::end)
				.thenComparing(CalendarEntry::type).thenComparing(CalendarEntry::id));
		return new CalendarView(zone, from, to, List.copyOf(entries));
	}

	private NewBlock rules(AddBusyBlock r, ZoneId zone, Instant now) {
		boolean timed = r.start() != null || r.end() != null;
		boolean allDay = Boolean.TRUE.equals(r.allDay()) || r.startDate() != null || r.endDate() != null;
		if (timed == allDay) {
			throw new InvalidBusyBlockException("Give either start and end, or allDay with startDate and endDate, not both and not neither.");
		}
		BusyBlockRules.Limits limits = new BusyBlockRules.Limits(properties.maxBlockDays(), BusyBlockRules.Limits.DEFAULT.maxAheadYears());
		if (timed) {
			return BusyBlockRules.timed(r.start(), r.end(), now, limits);
		}
		if (!Boolean.TRUE.equals(r.allDay())) {
			throw new InvalidBusyBlockException("Dates are for all-day blocks: set allDay to true.");
		}
		return BusyBlockRules.allDay(r.startDate(), r.endDate(), zone, now, limits);
	}

}
