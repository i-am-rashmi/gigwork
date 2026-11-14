package com.gigwork.core.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.identity.AccountNames;
import com.gigwork.core.profile.config.ProfileProperties;
import com.gigwork.core.profile.domain.InvalidCategoriesException;
import com.gigwork.core.profile.domain.InvalidLanguagesException;
import com.gigwork.core.profile.domain.InvalidLocationException;
import com.gigwork.core.profile.domain.InvalidSpecialisationsException;
import com.gigwork.core.profile.domain.ProfileType;
import com.gigwork.core.profile.persistence.ProfileStore;
import com.gigwork.core.reference.AreaResolution;
import com.gigwork.core.reference.LanguageInfo;
import com.gigwork.core.reference.LocalityInfo;
import com.gigwork.core.reference.ReferenceData;
import com.gigwork.core.reference.SpecialisationInfo;

/**
 * The profile rules that depend on what Reference Data says is live. The database tests cannot retire a row (the
 * reference snapshot is loaded once at startup), so Reference Data is stubbed here. Nothing is saved when a rule fails.
 */
class TalentProfileServiceValidationTest {

	private static final UUID LIVE = UUID.randomUUID();
	private static final UUID RETIRED = UUID.randomUUID();
	private static final UUID LANGUAGE_CATEGORY = UUID.randomUUID();
	private static final UUID LIVE_SPEC = UUID.randomUUID();
	private static final UUID RETIRED_SPEC = UUID.randomUUID();
	private static final UUID ACCOUNT = UUID.randomUUID();

	private final ReferenceData reference = mock(ReferenceData.class);
	private final ProfileStore store = mock(ProfileStore.class);
	private final TransactionTemplate transaction = mock(TransactionTemplate.class);
	private final AccountNames names = mock(AccountNames.class);
	private TalentProfileService service;

	@BeforeEach
	void setUp() {
		when(reference.isLiveCategory(LIVE)).thenReturn(true);
		when(reference.isLiveCategory(LANGUAGE_CATEGORY)).thenReturn(true);
		when(reference.isLiveCategory(RETIRED)).thenReturn(false);
		when(reference.isLanguageRelevant(LANGUAGE_CATEGORY)).thenReturn(true);
		when(reference.specialisation(LIVE_SPEC)).thenReturn(Optional.of(new SpecialisationInfo(LIVE_SPEC, LIVE, "wedding", "Wedding", true, 1)));
		when(reference.specialisation(RETIRED_SPEC)).thenReturn(Optional.of(new SpecialisationInfo(RETIRED_SPEC, LIVE, "old", "Old", false, 2)));
		when(reference.languages()).thenReturn(List.of(new LanguageInfo("kn", "Kannada", 1)));
		ProfileProperties properties = new ProfileProperties(5, 10, 10, new ProfileProperties.Location(50, 2000),
				new ProfileProperties.Portfolio(List.of("bit.ly")));
		when(names.firstName(ACCOUNT)).thenReturn(Optional.of("Asha"));
		service = new TalentProfileService(store, reference, names, properties, transaction,
				Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
	}

	private WorkCommand work(List<UUID> categories, List<UUID> specialisations, List<String> languages) {
		return new WorkCommand(ProfileType.INDIVIDUAL, null, "Headline", null, categories, specialisations, languages);
	}

	private void assertRefusedAndNothingSaved(Runnable save, Class<? extends RuntimeException> type) {
		assertThatThrownBy(save::run).isInstanceOf(type);
		verifyNoInteractions(transaction, store);
	}

	@Test
	void theWorkSectionNeedsAFirstNameAndSavesNothingWithout() {
		when(names.firstName(ACCOUNT)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.saveWork(ACCOUNT, work(List.of(LIVE), List.of(), List.of())))
				.isInstanceOf(com.gigwork.core.profile.domain.InvalidProfileException.class)
				.hasMessage("Add your first name before setting up your profile");
		verifyNoInteractions(transaction, store);
	}

	@Test
	void aRetiredCategoryIsRefused() {
		assertRefusedAndNothingSaved(() -> service.saveWork(ACCOUNT, work(List.of(LIVE, RETIRED), List.of(), List.of())), InvalidCategoriesException.class);
	}

	@Test
	void aRetiredSpecialisationIsRefused() {
		assertRefusedAndNothingSaved(() -> service.saveWork(ACCOUNT, work(List.of(LIVE), List.of(RETIRED_SPEC), List.of())), InvalidSpecialisationsException.class);
	}

	@Test
	void aSpecialisationOfAnotherCategoryIsRefused() {
		assertRefusedAndNothingSaved(() -> service.saveWork(ACCOUNT, work(List.of(LANGUAGE_CATEGORY), List.of(LIVE_SPEC), List.of())), InvalidSpecialisationsException.class);
	}

	@Test
	void aRetiredOrUnknownLanguageIsRefused() {
		assertRefusedAndNothingSaved(() -> service.saveWork(ACCOUNT, work(List.of(LANGUAGE_CATEGORY), List.of(), List.of("tcy"))), InvalidLanguagesException.class);
	}

	@Test
	void aLanguageWithNoLanguageRelevantCategoryIsRefused() {
		assertRefusedAndNothingSaved(() -> service.saveWork(ACCOUNT, work(List.of(LIVE), List.of(), List.of("kn"))), InvalidLanguagesException.class);
	}

	@Test
	void theLimitsAreTheConfiguredOnes() {
		ProfileProperties small = new ProfileProperties(2, 1, 1, new ProfileProperties.Location(50, 2000), new ProfileProperties.Portfolio(List.of()));
		TalentProfileService strict = new TalentProfileService(store, reference, names, small, transaction,
				Clock.systemUTC());
		UUID third = UUID.randomUUID();
		when(reference.isLiveCategory(third)).thenReturn(true);

		assertThatThrownBy(() -> strict.saveWork(ACCOUNT, work(List.of(LIVE, LANGUAGE_CATEGORY, third), List.of(), List.of())))
				.isInstanceOf(InvalidCategoriesException.class).hasMessageContaining("1 to 2");
		assertThatThrownBy(() -> strict.savePortfolio(ACCOUNT, List.of("a.example.com", "b.example.com")))
				.hasMessageContaining("at most 1");
	}

	@Test
	void aValidWorkSaveTakesTheLockedTransaction() {
		// the stubbed store has nothing to read back, which is the only way this save can end here
		assertThatThrownBy(() -> service.saveWork(ACCOUNT, work(List.of(LIVE), List.of(LIVE_SPEC), List.of())))
				.isInstanceOf(com.gigwork.core.profile.domain.ProfileNotFoundException.class);

		verify(transaction).executeWithoutResult(any());
	}

	@Test
	void aLocalityThatIsNotLiveIsRefused() {
		when(reference.locality(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.saveLocation(ACCOUNT, new LocationCommand(UUID.randomUUID(), null, null, 5, true)))
				.isInstanceOf(InvalidLocationException.class);
		verifyNoInteractions(transaction, store);
	}

	@Test
	void aMapPointIsRoundedBeforeItIsResolved() {
		when(reference.resolveArea(12.976, 77.605)).thenReturn(new AreaResolution(UUID.randomUUID(), UUID.randomUUID(), true, 0));
		when(reference.nearestLocality(12.976, 77.605, 2000)).thenReturn(Optional.of(new LocalityInfo(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Somewhere", null, 12.976, 77.605)));
		when(store.find(ACCOUNT)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.saveLocation(ACCOUNT, new LocationCommand(null, 12.97649, 77.60451, 5, true)))
				.isInstanceOf(com.gigwork.core.profile.domain.ProfileNotFoundException.class); // the stub store has no profile to read back
		verify(reference).resolveArea(12.976, 77.605);
	}

	@Test
	void roundingIsHalfUpAtThreeDecimals() {
		assertThat(TalentProfileService.round3(12.97649)).isEqualTo(12.976);
		assertThat(TalentProfileService.round3(12.9765)).isEqualTo(12.977);
		assertThat(TalentProfileService.round3(-77.6045)).isEqualTo(-77.605);
		assertThat(TalentProfileService.round3(77.0)).isEqualTo(77.0);
	}

}
