package com.gigwork.core.gigs.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.gigwork.core.gigs.persistence.StoredGig;
import com.gigwork.core.identity.AccountNames;
import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.CityInfo;
import com.gigwork.core.reference.LanguageInfo;
import com.gigwork.core.reference.ReferenceData;
import com.gigwork.core.reference.SpecialisationInfo;

/**
 * Turns stored gigs into what a given viewer may see (the visibility table of the PRD, NFR-34):
 * visitors get the gig, logged-in users also the Poster's first name, and the Poster also the gig's own bookkeeping.
 */
@Component
class GigViews {

	private final ReferenceData reference;
	private final AccountNames names;

	GigViews(ReferenceData reference, AccountNames names) {
		this.reference = reference;
		this.names = names;
	}

	/** @param viewer the logged-in caller, or empty for a visitor */
	List<GigView> of(List<StoredGig> gigs, Optional<UUID> viewer) {
		Map<UUID, Optional<String>> firstNames = new HashMap<>();
		return gigs.stream().map(g -> of(g, viewer, firstNames)).toList();
	}

	GigView of(StoredGig g, Optional<UUID> viewer) {
		return of(g, viewer, new HashMap<>());
	}

	private GigView of(StoredGig g, Optional<UUID> viewer, Map<UUID, Optional<String>> firstNames) {
		boolean poster = viewer.filter(g.posterId()::equals).isPresent();
		String firstName = viewer.isEmpty() ? null
				: firstNames.computeIfAbsent(g.posterId(), names::firstName).orElse(null);
		String specialisation = g.specialisationId() == null ? null
				: reference.specialisation(g.specialisationId()).map(SpecialisationInfo::name).orElse(null);
		String language = g.languageCode() == null ? null : reference.languages().stream()
				.filter(l -> l.code().equals(g.languageCode())).map(LanguageInfo::name).findFirst()
				.orElse(g.languageCode());
		return new GigView(g.id(), g.title(), g.description(), g.categoryId(),
				reference.category(g.categoryId()).map(CategoryInfo::name).orElse(null), g.specialisationId(),
				specialisation, g.languageCode(), language, g.startAt(), g.endAt(), g.workMode(), g.locationLabel(),
				g.cityId(), g.cityId() == null ? null : reference.city(g.cityId()).map(CityInfo::name).orElse(null),
				g.taOffered(), g.budgetMin(), g.budgetMax(), g.status(), g.publishedAt(), firstName,
				poster ? g.updatedAt() : null, poster ? g.closedAt() : null, poster ? g.closeReason() : null);
	}

}
