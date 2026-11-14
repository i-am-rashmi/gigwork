package com.gigwork.core.gigs.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.gigs.config.GigProperties;
import com.gigwork.core.gigs.domain.InvalidGigException;
import com.gigwork.core.gigs.domain.PosterNameRequiredException;
import com.gigwork.core.gigs.domain.WorkMode;
import com.gigwork.core.gigs.persistence.GigStore;
import com.gigwork.core.identity.AccountNames;
import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.LocationResolver;
import com.gigwork.core.reference.ReferenceData;
import com.gigwork.core.reference.SpecialisationInfo;

/** The rules that depend on reference data and need states the seed data does not have (retired rows); no database. */
class GigServiceValidationTest {

	private static final Instant NOW = Instant.parse("2026-11-14T06:30:00Z");
	private static final UUID POSTER = UUID.randomUUID();
	private static final UUID LIVE = UUID.randomUUID();
	private static final UUID RETIRED = UUID.randomUUID();
	private static final UUID LIVE_SPEC = UUID.randomUUID();
	private static final UUID RETIRED_SPEC = UUID.randomUUID();

	private final GigStore store = mock(GigStore.class);
	private final ReferenceData reference = mock(ReferenceData.class);
	private final AccountNames names = mock(AccountNames.class);
	private final TransactionTemplate transaction = mock(TransactionTemplate.class);
	private GigService service;

	@BeforeEach
	void setUp() {
		when(reference.category(LIVE)).thenReturn(Optional.of(category(LIVE, true)));
		when(reference.category(RETIRED)).thenReturn(Optional.of(category(RETIRED, false)));
		when(reference.specialisation(LIVE_SPEC)).thenReturn(Optional.of(new SpecialisationInfo(LIVE_SPEC, LIVE, "a", "A", true, 1)));
		when(reference.specialisation(RETIRED_SPEC)).thenReturn(Optional.of(new SpecialisationInfo(RETIRED_SPEC, LIVE, "b", "B", false, 2)));
		when(names.firstName(POSTER)).thenReturn(Optional.of("Asha"));
		service = new GigService(store, reference, mock(LocationResolver.class), names, mock(GigViews.class),
				new GigProperties(12, 14, 20, 50, 500), transaction, mock(ApplicationEventPublisher.class),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static CategoryInfo category(UUID id, boolean live) {
		return new CategoryInfo(id, "c", "C", "BOTH", false, live, 1, List.of());
	}

	private GigDraft draft(UUID category, UUID specialisation) {
		return new GigDraft(category, specialisation, null, "Title", "Description", NOW.plusSeconds(86_400),
				NOW.plusSeconds(90_000), WorkMode.REMOTE, null, null, null, null, null, null);
	}

	@Test
	void aRetiredCategoryIsNotOfferedForNewGigs() {
		assertThatThrownBy(() -> service.publish(POSTER, draft(RETIRED, null))).isInstanceOf(InvalidGigException.class)
				.hasMessageContaining("category");
		assertNothingWritten();
	}

	@Test
	void aRetiredSpecialisationIsNotOfferedForNewGigs() {
		assertThatThrownBy(() -> service.publish(POSTER, draft(LIVE, RETIRED_SPEC))).isInstanceOf(InvalidGigException.class)
				.hasMessageContaining("specialisation");
		assertNothingWritten();
	}

	@Test
	void aPosterWithoutAFirstNameIsRefusedBeforeAnythingElseIsLookedAt() {
		when(names.firstName(POSTER)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.publish(POSTER, draft(LIVE, LIVE_SPEC))).isInstanceOf(PosterNameRequiredException.class);
		verifyNoInteractions(reference);
		assertNothingWritten();
	}

	@Test
	void aMissingCategoryOrWorkModeIsRefused() {
		assertThatThrownBy(() -> service.publish(POSTER, draft(null, null))).isInstanceOf(InvalidGigException.class);
		assertNothingWritten();
	}

	private void assertNothingWritten() {
		verifyNoInteractions(store, transaction);
	}

}
