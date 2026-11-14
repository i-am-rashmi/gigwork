package com.gigwork.core.availability.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.gigwork.core.availability.domain.BusyBlockRules.Limits;

class BusyBlockRulesTest {

	private static final Instant NOW = Instant.parse("2026-11-14T06:30:00Z");
	private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
	private static final Limits LIMITS = Limits.DEFAULT;

	private static OffsetDateTime at(String text) {
		return OffsetDateTime.parse(text);
	}

	// ---- all-day: dates are read in the city's zone ---------------------------------------------------------------

	@Test
	void anAllDayBlockForFiveDecemberCoversFourDecember1830ZToFiveDecember1830Z() {
		var block = BusyBlockRules.allDay(LocalDate.parse("2026-12-05"), LocalDate.parse("2026-12-05"), KOLKATA, NOW, LIMITS);

		assertThat(block.start()).isEqualTo(Instant.parse("2026-12-04T18:30:00Z"));
		assertThat(block.end()).isEqualTo(Instant.parse("2026-12-05T18:30:00Z"));
		assertThat(block.allDay()).isTrue();
		assertThat(block.firstDate()).isEqualTo(LocalDate.parse("2026-12-05"));
		assertThat(block.lastDate()).isEqualTo(LocalDate.parse("2026-12-05"));
	}

	@Test
	void aMultiDayBlockEndsAtMidnightAfterTheLastDate() {
		var block = BusyBlockRules.allDay(LocalDate.parse("2026-12-05"), LocalDate.parse("2026-12-07"), KOLKATA, NOW, LIMITS);

		assertThat(block.start()).isEqualTo(Instant.parse("2026-12-04T18:30:00Z"));
		assertThat(block.end()).isEqualTo(Instant.parse("2026-12-07T18:30:00Z"));
	}

	@Test
	void datesFollowTheZoneIncludingDaylightSavingDays() {
		ZoneId newYork = ZoneId.of("America/New_York");
		Instant now = Instant.parse("2027-03-01T00:00:00Z");

		var springForward = BusyBlockRules.allDay(LocalDate.parse("2027-03-14"), LocalDate.parse("2027-03-14"), newYork, now, LIMITS);

		assertThat(springForward.start()).isEqualTo(Instant.parse("2027-03-14T05:00:00Z")); // still UTC-5 at midnight
		assertThat(springForward.end()).isEqualTo(Instant.parse("2027-03-15T04:00:00Z")); // UTC-4 after the change: a 23 hour day
	}

	@Test
	void todayCountsAsNotEntirelyInThePast() {
		Instant afternoonInKolkata = Instant.parse("2026-11-14T09:00:00Z"); // 14:30 on 14 November in Kolkata

		var today = BusyBlockRules.allDay(LocalDate.parse("2026-11-14"), LocalDate.parse("2026-11-14"), KOLKATA, afternoonInKolkata, LIMITS);

		assertThat(today.start()).isBefore(afternoonInKolkata);
		assertThatThrownBy(() -> BusyBlockRules.allDay(LocalDate.parse("2026-11-13"), LocalDate.parse("2026-11-13"), KOLKATA, afternoonInKolkata, LIMITS))
				.isInstanceOf(InvalidBusyBlockException.class).hasMessageContaining("past");
	}

	@Test
	void allDayLengthIsCountedInDaysAtMostNinety() {
		var ninety = BusyBlockRules.allDay(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-01").plusDays(89), KOLKATA, NOW, LIMITS);
		assertThat(ninety.end()).isEqualTo(Instant.parse("2027-03-01T18:30:00Z").minusSeconds(24 * 3600)); // midnight after 28 Feb

		assertThatThrownBy(() -> BusyBlockRules.allDay(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-01").plusDays(90), KOLKATA, NOW, LIMITS))
				.isInstanceOf(InvalidBusyBlockException.class).hasMessageContaining("90 days");
	}

	@Test
	void allDayNeedsBothDatesInOrder() {
		assertThatThrownBy(() -> BusyBlockRules.allDay(null, LocalDate.parse("2026-12-05"), KOLKATA, NOW, LIMITS)).isInstanceOf(InvalidBusyBlockException.class);
		assertThatThrownBy(() -> BusyBlockRules.allDay(LocalDate.parse("2026-12-05"), null, KOLKATA, NOW, LIMITS)).isInstanceOf(InvalidBusyBlockException.class);
		assertThatThrownBy(() -> BusyBlockRules.allDay(LocalDate.parse("2026-12-06"), LocalDate.parse("2026-12-05"), KOLKATA, NOW, LIMITS))
				.isInstanceOf(InvalidBusyBlockException.class).hasMessageContaining("before");
	}

	// ---- timed ----------------------------------------------------------------------------------------------------------

	@Test
	void aTimedBlockKeepsItsInstantsWhateverTheOffset() {
		var block = BusyBlockRules.timed(at("2026-12-05T10:00:00+05:30"), at("2026-12-05T14:00:00+05:30"), NOW, LIMITS);

		assertThat(block.start()).isEqualTo(Instant.parse("2026-12-05T04:30:00Z"));
		assertThat(block.end()).isEqualTo(Instant.parse("2026-12-05T08:30:00Z"));
		assertThat(block.allDay()).isFalse();
		assertThat(block.firstDate()).isNull();
	}

	@Test
	void timesAreCutToMicroseconds() {
		var block = BusyBlockRules.timed(at("2026-12-05T10:00:00.123456789Z"), at("2026-12-05T11:00:00.987654321Z"), NOW, LIMITS);

		assertThat(block.start()).isEqualTo(Instant.parse("2026-12-05T10:00:00.123456Z"));
		assertThat(block.end()).isEqualTo(Instant.parse("2026-12-05T11:00:00.987654Z"));
	}

	@Test
	void theEndMustBeAfterTheStart() {
		assertThatThrownBy(() -> BusyBlockRules.timed(at("2026-12-05T10:00:00Z"), at("2026-12-05T10:00:00Z"), NOW, LIMITS))
				.isInstanceOf(InvalidBusyBlockException.class).hasMessageContaining("after the start");
		assertThatThrownBy(() -> BusyBlockRules.timed(at("2026-12-05T10:00:00Z"), at("2026-12-05T09:00:00Z"), NOW, LIMITS))
				.isInstanceOf(InvalidBusyBlockException.class);
		assertThatThrownBy(() -> BusyBlockRules.timed(null, at("2026-12-05T09:00:00Z"), NOW, LIMITS)).isInstanceOf(InvalidBusyBlockException.class);
		assertThatThrownBy(() -> BusyBlockRules.timed(at("2026-12-05T09:00:00Z"), null, NOW, LIMITS)).isInstanceOf(InvalidBusyBlockException.class);
	}

	@Test
	void aBlockThatHasAlreadyEndedIsRefusedButOneInProgressIsFine() {
		assertThatThrownBy(() -> BusyBlockRules.timed(at("2026-11-13T10:00:00Z"), at("2026-11-14T06:30:00Z"), NOW, LIMITS)) // ends exactly now
				.isInstanceOf(InvalidBusyBlockException.class).hasMessageContaining("past");
		assertThatThrownBy(() -> BusyBlockRules.timed(at("2026-11-01T10:00:00Z"), at("2026-11-02T10:00:00Z"), NOW, LIMITS))
				.isInstanceOf(InvalidBusyBlockException.class);
		assertThat(BusyBlockRules.timed(at("2026-11-13T10:00:00Z"), at("2026-11-14T06:30:00.000001Z"), NOW, LIMITS)).isNotNull(); // still running
	}

	@Test
	void aBlockMustStartWithinOneYear() {
		assertThat(BusyBlockRules.timed(at("2027-11-14T06:30:00Z"), at("2027-11-15T06:30:00Z"), NOW, LIMITS)).isNotNull(); // starts exactly a year ahead
		assertThatThrownBy(() -> BusyBlockRules.timed(at("2027-11-14T06:30:00.000001Z"), at("2027-11-15T06:30:00Z"), NOW, LIMITS))
				.isInstanceOf(InvalidBusyBlockException.class).hasMessageContaining("within 1 year");
	}

	@Test
	void aBlockIsAtMostNinetyDaysLong() {
		assertThat(BusyBlockRules.timed(at("2026-12-01T00:00:00Z"), at("2027-03-01T00:00:00Z"), NOW, LIMITS)).isNotNull(); // exactly 90 days
		assertThatThrownBy(() -> BusyBlockRules.timed(at("2026-12-01T00:00:00Z"), at("2027-03-01T00:00:00.000001Z"), NOW, LIMITS))
				.isInstanceOf(InvalidBusyBlockException.class).hasMessageContaining("90 days");
	}

}
