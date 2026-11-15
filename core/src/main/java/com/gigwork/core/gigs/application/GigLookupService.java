package com.gigwork.core.gigs.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.gigwork.core.gigs.GigLookup;
import com.gigwork.core.gigs.GigState;
import com.gigwork.core.gigs.GigSummary;
import com.gigwork.core.gigs.persistence.GigStore;
import com.gigwork.core.gigs.persistence.StoredGig;
import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.ReferenceData;

/** Answers other modules' read-only questions about gigs. */
@Service
class GigLookupService implements GigLookup {

	private final GigStore store;
	private final ReferenceData reference;

	GigLookupService(GigStore store, ReferenceData reference) {
		this.store = store;
		this.reference = reference;
	}

	@Override
	public Optional<GigSummary> find(UUID gigId) {
		return store.find(gigId).map(this::summary);
	}

	@Override
	public Map<UUID, GigSummary> findAll(Collection<UUID> gigIds) {
		if (gigIds.isEmpty()) {
			return Map.of();
		}
		return store.findAll(gigIds.stream().distinct().toList()).stream().map(this::summary)
				.collect(Collectors.toMap(GigSummary::id, Function.identity()));
	}

	private GigSummary summary(StoredGig g) {
		return new GigSummary(g.id(), g.posterId(), g.categoryId(),
				reference.category(g.categoryId()).map(CategoryInfo::name).orElse(null), g.title(), g.startAt(),
				g.endAt(), g.workMode().name(), g.locationLabel(), GigState.valueOf(g.status().name()));
	}

}
