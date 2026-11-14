package com.gigwork.core.availability.web;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gigwork.core.availability.application.AddBusyBlock;
import com.gigwork.core.availability.application.BusyBlockService;
import com.gigwork.core.availability.web.dto.BusyBlockRequest;
import com.gigwork.core.availability.web.dto.CalendarEntryResponse;
import com.gigwork.core.availability.web.dto.CalendarResponse;
import com.gigwork.core.platform.CurrentAccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** The signed-in Talent's busy blocks and calendar (FR-7). Needs a token; nothing here is ever shown to anyone else. */
@RestController
@RequestMapping("/me")
@Tag(name = "Availability", description = "Your busy blocks and calendar (private to you)")
class AvailabilityController {

	private final BusyBlockService service;

	AvailabilityController(BusyBlockService service) {
		this.service = service;
	}

	@Operation(summary = "Add a busy block",
			description = "Timed: start and end with a UTC offset. All-day: allDay true with startDate and endDate "
					+ "(inclusive), read in your city's time zone. Can't be entirely in the past, must start within a "
					+ "year, at most 90 days long, at most 200 upcoming blocks. Blocks can't be edited: remove and add.")
	@ApiResponse(responseCode = "400", description = "A rule is broken; the message says which")
	@ApiResponse(responseCode = "409", description = "You have no Talent profile yet")
	@PostMapping("/busy-blocks")
	@ResponseStatus(HttpStatus.CREATED)
	CalendarEntryResponse add(@Valid @RequestBody BusyBlockRequest request) {
		return CalendarEntryResponse.of(service.add(CurrentAccount.required(),
				new AddBusyBlock(request.start(), request.end(), request.allDay(), request.startDate(), request.endDate())));
	}

	@Operation(summary = "Remove one of your busy blocks")
	@ApiResponse(responseCode = "404", description = "No such block of yours")
	@DeleteMapping("/busy-blocks/{id}")
	ResponseEntity<Void> remove(@PathVariable UUID id) {
		return service.remove(CurrentAccount.required(), id) ? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

	@Operation(summary = "Your busy blocks and bookings between two dates",
			description = "from and to are dates (YYYY-MM-DD, both inclusive) read in your city's time zone; at most 92 days.")
	@ApiResponse(responseCode = "400", description = "Missing or reversed dates, or more than 92 days")
	@GetMapping("/calendar")
	CalendarResponse calendar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return CalendarResponse.of(service.calendar(CurrentAccount.required(), from, to));
	}

}
