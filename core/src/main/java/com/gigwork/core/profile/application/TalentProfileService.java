package com.gigwork.core.profile.application;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.identity.AccountNames;
import com.gigwork.core.profile.TalentProfileSummary;
import com.gigwork.core.profile.TalentProfiles;
import com.gigwork.core.profile.config.ProfileProperties;
import com.gigwork.core.profile.domain.InvalidCategoriesException;
import com.gigwork.core.profile.domain.InvalidLanguagesException;
import com.gigwork.core.profile.domain.InvalidLinkException;
import com.gigwork.core.profile.domain.InvalidLocationException;
import com.gigwork.core.profile.domain.InvalidMemberCountException;
import com.gigwork.core.profile.domain.InvalidProfileException;
import com.gigwork.core.profile.domain.ProfileNotFoundException;
import com.gigwork.core.profile.domain.InvalidSpecialisationsException;
import com.gigwork.core.profile.domain.PortfolioLink;
import com.gigwork.core.profile.domain.ProfileText;
import com.gigwork.core.profile.domain.ProfileType;
import com.gigwork.core.profile.persistence.ProfileStore;
import com.gigwork.core.profile.persistence.StoredProfile;
import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.LanguageInfo;
import com.gigwork.core.reference.LocationResolver;
import com.gigwork.core.reference.ReferenceData;
import com.gigwork.core.reference.ResolvedLocation;
import com.gigwork.core.reference.SpecialisationInfo;
import com.gigwork.core.reference.ZoneInfo;

/**
 * A Talent's profile, saved in three sections (work, location, portfolio) and shown to its owner, to logged-in users
 * and to visitors (FR-4 to FR-6a, FR-8, FR-9).
 *
 * <p>Each save is validated first (no database, no lock), then runs in one transaction that creates the profile
 * row if needed and locks it; everything else happens under that lock, so concurrent saves of one Talent are
 * serialised and the lists in the database are always exactly one request's list.
 */
@Service
public class TalentProfileService implements TalentProfiles {

	static final int MEMBER_COUNT_MIN = 2;
	static final int MEMBER_COUNT_MAX = 50;

	private final ProfileStore store;
	private final ReferenceData reference;
	private final AccountNames names;
	private final ProfileProperties properties;
	private final LocationResolver locations;
	private final TransactionTemplate transaction;
	private final Clock clock;

	TalentProfileService(ProfileStore store, ReferenceData reference, AccountNames names, ProfileProperties properties,
			LocationResolver locations, TransactionTemplate transaction, Clock clock) {
		this.store = store;
		this.reference = reference;
		this.names = names;
		this.properties = properties;
		this.locations = locations;
		this.transaction = transaction;
		this.clock = clock;
	}

	// ---- saving the three sections --------------------------------------------------------------------------

	/** The first name every Talent has (shown to logged-in users) must exist before a profile is set up (FR-4). */
	static final String FIRST_NAME_REQUIRED = "Add your first name before setting up your profile";

	public ProfileView saveWork(UUID accountId, WorkCommand command) {
		if (names.firstName(accountId).isEmpty()) {
			throw new InvalidProfileException(FIRST_NAME_REQUIRED);
		}
		ProfileStore.Work work = validate(command);
		transaction.executeWithoutResult(status -> {
			store.lockOrCreate(accountId, clock.instant());
			store.replaceWork(accountId, work, clock.instant());
		});
		return owner(accountId);
	}

	public ProfileView saveLocation(UUID accountId, LocationCommand command) {
		ProfileStore.Location location = resolve(command);
		transaction.executeWithoutResult(status -> {
			store.lockOrCreate(accountId, clock.instant());
			store.replaceLocation(accountId, location, clock.instant());
		});
		return owner(accountId);
	}

	public ProfileView savePortfolio(UUID accountId, List<String> rawLinks) {
		List<ProfileStore.NewLink> links = validateLinks(rawLinks);
		transaction.executeWithoutResult(status -> {
			store.lockOrCreate(accountId, clock.instant());
			store.replaceLinks(accountId, links, clock.instant());
		});
		return owner(accountId);
	}

	public ProfileView setHidden(UUID accountId, boolean hidden) {
		transaction.executeWithoutResult(status -> {
			store.lockOrCreate(accountId, clock.instant());
			store.setHidden(accountId, hidden, clock.instant());
		});
		return owner(accountId);
	}

	// ---- reading ---------------------------------------------------------------------------------------------

	/** The owner's own profile: everything but coordinates. Empty if nothing has been saved yet. */
	public Optional<ProfileView> ownerProfile(UUID accountId) {
		return store.find(accountId).map(this::view);
	}

	private ProfileView owner(UUID accountId) {
		return ownerProfile(accountId).orElseThrow(() -> new ProfileNotFoundException(accountId));
	}

	/**
	 * A profile as the caller may see it. Empty (a 404 for the caller) if it does not exist or is not ACTIVE,
	 * unless the caller is its owner (FR-8). A first name is included only for a logged-in caller.
	 */
	public Optional<PublicProfile> publicProfile(UUID accountId, Optional<UUID> caller) {
		boolean owner = caller.filter(accountId::equals).isPresent();
		return store.find(accountId).filter(profile -> profile.isActive() || owner).map(profile -> {
			Optional<String> firstName = caller.isPresent() ? names.firstName(accountId) : Optional.empty();
			return new PublicProfile(view(profile), firstName, caller.isPresent());
		});
	}

	@Override
	public Optional<TalentProfileSummary> summary(UUID accountId) {
		return store.find(accountId).map(p -> new TalentProfileSummary(p.accountId(), p.status(), p.cityId()));
	}

	// ---- work section: validation against the rules and Reference Data ----------------------------------------

	private ProfileStore.Work validate(WorkCommand c) {
		String headline = c.headline() == null ? "" : c.headline().strip();
		if (headline.isEmpty() || headline.length() > 80) {
			throw new InvalidProfileException("The headline needs 1 to 80 characters.");
		}
		String bio = c.bio() == null || c.bio().isBlank() ? null : c.bio().strip();
		if (bio != null && bio.length() > 1000) {
			throw new InvalidProfileException("The bio can have at most 1000 characters.");
		}
		ProfileText.requireNoContactDetails(headline);
		ProfileText.requireNoContactDetails(bio);

		if (c.profileType() == null) {
			throw new InvalidProfileException("Choose whether the profile is for an individual or a group.");
		}
		Integer memberCount = c.memberCount();
		if (c.profileType() == ProfileType.INDIVIDUAL && memberCount != null) {
			throw new InvalidMemberCountException("An individual has no member count.");
		}
		if (memberCount != null && (memberCount < MEMBER_COUNT_MIN || memberCount > MEMBER_COUNT_MAX)) {
			throw new InvalidMemberCountException(
					"A group has " + MEMBER_COUNT_MIN + " to " + MEMBER_COUNT_MAX + " members.");
		}

		List<UUID> categoryIds = c.categoryIds() == null ? List.of() : c.categoryIds();
		if (categoryIds.isEmpty() || categoryIds.size() > properties.maxCategories()) {
			throw new InvalidCategoriesException("Choose 1 to " + properties.maxCategories() + " categories.");
		}
		if (new HashSet<>(categoryIds).size() != categoryIds.size()) {
			throw new InvalidCategoriesException("A category is listed twice.");
		}
		for (UUID id : categoryIds) {
			if (!reference.isLiveCategory(id)) {
				throw new InvalidCategoriesException("A chosen category does not exist or is no longer offered.");
			}
		}

		List<UUID> specialisationIds = c.specialisationIds() == null ? List.of() : c.specialisationIds();
		if (specialisationIds.size() > properties.maxSpecialisations()) {
			throw new InvalidSpecialisationsException(
					"Choose at most " + properties.maxSpecialisations() + " specialisations.");
		}
		if (new HashSet<>(specialisationIds).size() != specialisationIds.size()) {
			throw new InvalidSpecialisationsException("A specialisation is listed twice.");
		}
		Set<UUID> chosenCategories = new HashSet<>(categoryIds);
		List<com.gigwork.core.profile.persistence.StoredProfile.Specialisation> specialisations = specialisationIds.stream()
				.map(id -> {
					SpecialisationInfo spec = reference.specialisation(id).filter(SpecialisationInfo::live).orElseThrow(
							() -> new InvalidSpecialisationsException(
									"A chosen specialisation does not exist or is no longer offered."));
					if (!chosenCategories.contains(spec.categoryId())) {
						throw new InvalidSpecialisationsException(
								"Each specialisation must belong to one of your chosen categories.");
					}
					return new StoredProfile.Specialisation(spec.categoryId(), spec.id());
				}).toList();

		List<String> languages = c.languageCodes() == null ? List.of() : c.languageCodes();
		if (new HashSet<>(languages).size() != languages.size()) {
			throw new InvalidLanguagesException("A language is listed twice.");
		}
		Set<String> liveLanguages = reference.languages().stream().map(LanguageInfo::code).collect(Collectors.toSet());
		for (String code : languages) {
			if (!liveLanguages.contains(code)) {
				throw new InvalidLanguagesException("A chosen language does not exist or is no longer offered.");
			}
		}
		if (!languages.isEmpty() && categoryIds.stream().noneMatch(reference::isLanguageRelevant)) {
			throw new InvalidLanguagesException("Languages can only be listed when one of your categories is about "
					+ "performing or writing in a language, such as Anchor, Singer or Content writer.");
		}
		return new ProfileStore.Work(c.profileType().name(), memberCount, headline, bio, categoryIds, specialisations,
				languages);
	}

	// ---- location section ---------------------------------------------------------------------------------------

	private ProfileStore.Location resolve(LocationCommand c) {
		try {
			ResolvedLocation place = locations.resolve(c.localityId(), c.lat(), c.lng());
			return new ProfileStore.Location(place.lat(), place.lng(), place.cityId(), place.zoneId(), place.label(),
					place.localityId(), c.serviceRadiusKm(), c.willingToTravel());
		}
		catch (com.gigwork.core.reference.InvalidLocationException e) {
			throw new InvalidLocationException(e.getMessage());
		}
	}

	/** Map points are kept to 3 decimals (about 110 m): enough for matching, too coarse to find a home (FR-5). */
	static double round3(double value) {
		return LocationResolver.round3(value);
	}

	// ---- portfolio section ----------------------------------------------------------------------------------------

	private List<ProfileStore.NewLink> validateLinks(List<String> raw) {
		List<String> input = raw == null ? List.of() : raw;
		if (input.size() > properties.maxLinks()) {
			throw new InvalidLinkException("You can add at most " + properties.maxLinks() + " portfolio links.");
		}
		Set<String> seen = new HashSet<>();
		return input.stream().map(text -> PortfolioLink.parse(text, properties.portfolio().blockedHosts())).map(link -> {
			if (!seen.add(link.url())) {
				throw new InvalidLinkException("The same portfolio link is listed twice.");
			}
			return new ProfileStore.NewLink(UUID.randomUUID(), link.url(), link.type().name());
		}).toList();
	}

	// ---- assembling a view ---------------------------------------------------------------------------------------

	private ProfileView view(StoredProfile p) {
		List<ProfileView.Named> categories = p.categoryIds().stream().map(reference::category).flatMap(Optional::stream)
				.sorted(java.util.Comparator.comparingInt(CategoryInfo::displayOrder))
				.map(c -> new ProfileView.Named(c.id(), c.slug(), c.name())).toList();
		List<ProfileView.NamedSpecialisation> specialisations = p.specialisations().stream()
				.map(s -> reference.specialisation(s.specialisationId())).flatMap(Optional::stream)
				.sorted(java.util.Comparator.comparingInt(SpecialisationInfo::displayOrder))
				.map(s -> new ProfileView.NamedSpecialisation(s.id(), s.categoryId(), s.slug(), s.name())).toList();
		java.util.Map<String, LanguageInfo> byCode = reference.languages().stream()
				.collect(Collectors.toMap(LanguageInfo::code, l -> l));
		List<ProfileView.Language> languages = p.languageCodes().stream().map(byCode::get).filter(java.util.Objects::nonNull)
				.sorted(java.util.Comparator.comparingInt(LanguageInfo::displayOrder))
				.map(l -> new ProfileView.Language(l.code(), l.name())).toList();
		String cityName = p.cityId() == null ? null : reference.city(p.cityId()).map(c -> c.name()).orElse(null);
		String zoneName = p.zoneId() == null ? null : reference.zone(p.zoneId()).map(ZoneInfo::name).orElse(null);
		List<ProfileView.Link> links = p.links().stream().map(l -> new ProfileView.Link(l.url(), l.type())).toList();
		return new ProfileView(p.accountId(), p.status(), p.hidden(), p.profileType(), p.memberCount(), p.headline(),
				p.bio(), categories, specialisations, languages, p.locationSaved(), p.locationLabel(), p.cityId(),
				cityName, p.zoneId(), zoneName, p.localityId(), p.serviceRadiusKm(), p.willingToTravel(), links);
	}

}
