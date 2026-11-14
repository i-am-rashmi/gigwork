package com.gigwork.core.gigs.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.gigs.GigCancelled;
import com.gigwork.core.gigs.GigChanged;
import com.gigwork.core.gigs.GigPublished;
import com.gigwork.core.gigs.config.GigProperties;
import com.gigwork.core.gigs.domain.GigNotFoundException;
import com.gigwork.core.gigs.domain.GigNotOpenException;
import com.gigwork.core.gigs.domain.GigRules;
import com.gigwork.core.gigs.domain.GigStatus;
import com.gigwork.core.gigs.domain.InvalidGigException;
import com.gigwork.core.gigs.domain.PosterNameRequiredException;
import com.gigwork.core.gigs.domain.WorkMode;
import com.gigwork.core.gigs.persistence.GigFields;
import com.gigwork.core.gigs.persistence.GigStore;
import com.gigwork.core.gigs.persistence.StoredGig;
import com.gigwork.core.identity.AccountNames;
import com.gigwork.core.reference.CategoryInfo;
import com.gigwork.core.reference.InvalidLocationException;
import com.gigwork.core.reference.LocationResolver;
import com.gigwork.core.reference.ReferenceData;
import com.gigwork.core.reference.ResolvedLocation;
import com.gigwork.core.reference.SpecialisationInfo;

/**
 * Publishing, editing and cancelling gigs (FR-15, FR-16, FR-18, FR-19).
 *
 * <p>Every request is validated first (no lock, no write). A write is then one conditional statement in a transaction
 * that also publishes the event, so the event exists exactly when the change does. Whether a change was refused
 * because the gig isn't the caller's, isn't open any more or has started is worked out only after the statement
 * matched no row, so the check and the change can never disagree.
 */
@Service
public class GigService {

	private final GigStore store;
	private final ReferenceData reference;
	private final LocationResolver locations;
	private final AccountNames names;
	private final GigViews views;
	private final GigProperties properties;
	private final TransactionTemplate transaction;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	GigService(GigStore store, ReferenceData reference, LocationResolver locations, AccountNames names, GigViews views,
			GigProperties properties, TransactionTemplate transaction, ApplicationEventPublisher events, Clock clock) {
		this.store = store;
		this.reference = reference;
		this.locations = locations;
		this.names = names;
		this.views = views;
		this.properties = properties;
		this.transaction = transaction;
		this.events = events;
		this.clock = clock;
	}

	/** Publishes a gig as OPEN (FR-15). */
	public GigView publish(UUID posterId, GigDraft draft) {
		if (names.firstName(posterId).isEmpty()) {
			throw new PosterNameRequiredException();
		}
		Instant now = clock.instant();
		GigFields fields = validate(draft, now);
		UUID id = UUID.randomUUID();
		transaction.executeWithoutResult(status -> {
			store.insert(id, posterId, fields, now);
			events.publishEvent(new GigPublished(id, posterId, fields.categoryId(), fields.specialisationId(),
					fields.languageCode(), fields.workMode().name(), fields.cityId(), fields.zoneId(), fields.startAt(),
					fields.endAt(), now));
		});
		return views.of(store.find(id).orElseThrow(), Optional.of(posterId));
	}

	/**
	 * Replaces the editable fields of an open gig that has not started (FR-18). The category and the work mode must
	 * be the gig's current ones.
	 */
	public GigView edit(UUID posterId, UUID gigId, GigDraft draft) {
		Instant now = clock.instant();
		GigFields fields = validate(draft, now);
		boolean changed = Boolean.TRUE.equals(transaction.execute(status -> {
			Optional<GigStore.Changes> changes = store.update(gigId, posterId, fields, now);
			changes.filter(c -> c.dateTime() || c.location() || c.matching())
					.ifPresent(c -> events.publishEvent(new GigChanged(gigId, posterId, fields.categoryId(),
							fields.specialisationId(), fields.languageCode(), fields.workMode().name(), fields.cityId(),
							fields.zoneId(), fields.startAt(), fields.endAt(), c.dateTime(), c.location(), c.matching(),
							now)));
			return changes.isPresent();
		}));
		if (!changed) {
			throw refusal(posterId, gigId, now, fields);
		}
		return views.of(store.find(gigId).orElseThrow(), Optional.of(posterId));
	}

	/** Cancels the Poster's open gig (FR-19). Cancelling a filled gig comes with hiring. */
	public GigView cancel(UUID posterId, UUID gigId, String reason) {
		Instant now = clock.instant();
		String trimmed = reason == null || reason.isBlank() ? null : reason.strip();
		if (trimmed != null && trimmed.codePointCount(0, trimmed.length()) > 500) {
			throw new InvalidGigException("The reason can be at most 500 characters.");
		}
		boolean cancelled = Boolean.TRUE.equals(transaction.execute(status -> {
			boolean done = store.cancel(gigId, posterId, trimmed, now);
			if (done) {
				events.publishEvent(new GigCancelled(gigId, posterId, now));
			}
			return done;
		}));
		if (!cancelled) {
			throw refusal(posterId, gigId, now, null);
		}
		return views.of(store.find(gigId).orElseThrow(), Optional.of(posterId));
	}

	/**
	 * Why a conditional statement matched no row. 404 for a gig that doesn't exist or isn't the caller's (never says
	 * which); 409 when it is the caller's but not open or already started; 400 when only the category or the work
	 * mode differ.
	 */
	private RuntimeException refusal(UUID posterId, UUID gigId, Instant now, GigFields attempted) {
		Optional<StoredGig> gig = store.find(gigId).filter(g -> g.posterId().equals(posterId));
		if (gig.isEmpty()) {
			return new GigNotFoundException();
		}
		StoredGig g = gig.get();
		if (g.status() != GigStatus.OPEN || (attempted != null && !g.startAt().isAfter(now))) {
			return new GigNotOpenException();
		}
		if (attempted != null && (!g.categoryId().equals(attempted.categoryId()) || g.workMode() != attempted.workMode())) {
			return new InvalidGigException("The category and the work mode of a published gig can't be changed.");
		}
		// still open and unchanged in the ways we check: it changed again while we were looking
		return new GigNotOpenException();
	}

	// ---- the rules that need reference data ----------------------------------------------------------------

	private GigFields validate(GigDraft d, Instant now) {
		if (d.categoryId() == null || d.workMode() == null) {
			throw new InvalidGigException("A category and a work mode are required.");
		}
		CategoryInfo category = reference.category(d.categoryId()).filter(CategoryInfo::live)
				.orElseThrow(() -> new InvalidGigException("Choose a category we offer."));
		if (!category.workMode().equals("BOTH") && !category.workMode().equals(d.workMode().name())) {
			throw new InvalidGigException("This category doesn't allow " + (d.workMode() == WorkMode.REMOTE
					? "remote" : "on-site") + " gigs.");
		}
		if (d.specialisationId() != null) {
			reference.specialisation(d.specialisationId()).filter(SpecialisationInfo::live)
					.filter(s -> s.categoryId().equals(category.id()))
					.orElseThrow(() -> new InvalidGigException("Choose a specialisation of the chosen category."));
		}
		String language = d.languageCode() == null || d.languageCode().isBlank() ? null : d.languageCode().strip();
		if (language != null) {
			if (!category.languageRelevant()) {
				throw new InvalidGigException("A language can only be given for a category about performing or "
						+ "writing in a language, such as Anchor, Singer or Content writer.");
			}
			if (reference.languages().stream().noneMatch(l -> l.code().equals(language))) {
				throw new InvalidGigException("Choose a language we offer.");
			}
		}
		String title = GigRules.text(d.title(), "title", GigRules.TITLE_MAX);
		String description = GigRules.text(d.description(), "description", GigRules.DESCRIPTION_MAX);
		Instant start = d.startAt() == null ? null : d.startAt().truncatedTo(ChronoUnit.MICROS);
		Instant end = d.endAt() == null ? null : d.endAt().truncatedTo(ChronoUnit.MICROS);
		GigRules.dates(start, end, now, properties.maxMonthsAhead(), properties.maxLengthDays());
		GigRules.Budget budget = GigRules.budget(d.budgetMin(), d.budgetMax());
		Long min = budget == null ? null : budget.min();
		Long max = budget == null ? null : budget.max();

		boolean hasPlace = d.localityId() != null || d.lat() != null || d.lng() != null;
		if (d.workMode() == WorkMode.REMOTE) {
			if (hasPlace || d.taOffered() != null) {
				throw new InvalidGigException("A remote gig has no location and no travel allowance.");
			}
			return new GigFields(category.id(), d.specialisationId(), language, title, description, start, end,
					WorkMode.REMOTE, null, null, null, null, null, null, null, min, max);
		}
		if (d.taOffered() == null) {
			throw new InvalidGigException("Say whether travel allowance is offered for an on-site gig.");
		}
		ResolvedLocation place;
		try {
			place = locations.resolve(d.localityId(), d.lat(), d.lng());
		}
		catch (InvalidLocationException e) {
			throw new InvalidGigException(e.getMessage());
		}
		return new GigFields(category.id(), d.specialisationId(), language, title, description, start, end,
				WorkMode.ON_SITE, place.lat(), place.lng(), place.cityId(), place.zoneId(), place.label(),
				place.localityId(), d.taOffered(), min, max);
	}

}
