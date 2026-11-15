package com.gigwork.core.hiring.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.gigwork.core.gigs.GigLookup;
import com.gigwork.core.gigs.GigSummary;
import com.gigwork.core.hiring.config.HiringProperties;
import com.gigwork.core.hiring.domain.ApplicationCursor;
import com.gigwork.core.hiring.domain.ApplicationNotFoundException;
import com.gigwork.core.hiring.domain.InvalidApplicationException;
import com.gigwork.core.hiring.persistence.ApplicationStore;
import com.gigwork.core.hiring.persistence.ApplicationStore.Keyset;
import com.gigwork.core.hiring.persistence.StoredApplication;
import com.gigwork.core.hiring.persistence.ShortlistStore;
import com.gigwork.core.hiring.persistence.ShortlistStore.Entry;
import com.gigwork.core.identity.AccountNames;
import com.gigwork.core.profile.ApplicantProfile;
import com.gigwork.core.profile.TalentProfiles;

/**
 * The two lists of applications. Each page is one query for the applications plus one batch call to the module
 * that owns the rest (gigs, or profiles), never a call per row.
 */
@Service
public class ApplicationQueries {

	private final ApplicationStore store;
	private final ShortlistStore shortlists;
	private final GigLookup gigs;
	private final TalentProfiles profiles;
	private final AccountNames names;
	private final HiringProperties properties;

	ApplicationQueries(ApplicationStore store, ShortlistStore shortlists, GigLookup gigs, TalentProfiles profiles,
			AccountNames names, HiringProperties properties) {
		this.store = store;
		this.shortlists = shortlists;
		this.names = names;
		this.gigs = gigs;
		this.profiles = profiles;
		this.properties = properties;
	}

	/** The Talent's own applications in every status, newest first, each with its gig as it is now (FR-22). */
	public MyApplicationsPage mine(UUID talentId, String cursor, Integer requestedSize) {
		int size = pageSize(requestedSize);
		List<StoredApplication> fetched = store.listByTalent(talentId, keyset(cursor), size + 1);
		List<StoredApplication> shown = fetched.size() > size ? fetched.subList(0, size) : fetched;
		Map<UUID, GigSummary> gigsById = gigs.findAll(shown.stream().map(StoredApplication::gigId).toList());
		Map<UUID, Entry> entries = entries(shown);
		List<MyApplication> items = shown.stream().map(a -> new MyApplication(a.id(), a.gigId(), a.status(), a.note(),
				a.appliedAt(), a.withdrawnAt(), gigsById.get(a.gigId()),
				entries.containsKey(a.id()) && entries.get(a.id()).isCurrent())).toList();
		return new MyApplicationsPage(items, next(fetched, size));
	}

	/**
	 * The gig's active applications, oldest first, for the gig's Poster only (FR-24). A gig that doesn't exist or
	 * isn't the caller's is a 404 before anything else is looked at.
	 */
	public ApplicantsPage applicants(UUID posterId, UUID gigId, String cursor, Integer requestedSize) {
		gigs.find(gigId).filter(g -> g.posterId().equals(posterId)).orElseThrow(ApplicationNotFoundException::new);
		int size = pageSize(requestedSize);
		List<StoredApplication> fetched = store.listActiveByGig(gigId, keyset(cursor), size + 1);
		List<StoredApplication> shown = fetched.size() > size ? fetched.subList(0, size) : fetched;
		Map<UUID, ApplicantProfile> profilesById = profiles
				.applicantProfiles(shown.stream().map(StoredApplication::talentId).toList());
		Map<UUID, Entry> entries = entries(shown);
		List<Applicant> items = shown.stream().map(a -> {
			Entry entry = entries.get(a.id());
			boolean current = entry != null && entry.isCurrent();
			return new Applicant(a.id(), a.talentId(), a.note(), a.appliedAt(), profilesById.get(a.talentId()), current,
					current && entry.posterShares(), current && entry.talentConsents());
		}).toList();
		return new ApplicantsPage(items, next(fetched, size));
	}

	/**
	 * The Talent's own Shortlisted tab (FR-26), newest shortlisted first. Closed gigs stay, with their status. One
	 * query for the entries, one batch call for the gigs and one for the Posters' first names.
	 */
	public ShortlistedPage shortlisted(UUID talentId, String cursor, Integer requestedSize) {
		int size = pageSize(requestedSize);
		ShortlistStore.Keyset after = null;
		if (cursor != null && !cursor.isBlank()) {
			ApplicationCursor decoded = ApplicationCursor.decode(cursor);
			after = new ShortlistStore.Keyset(decoded.at(), decoded.id());
		}
		List<Entry> fetched = shortlists.listCurrentByTalent(talentId, after, size + 1);
		List<Entry> shown = fetched.size() > size ? fetched.subList(0, size) : fetched;
		Map<UUID, GigSummary> gigsById = gigs.findAll(shown.stream().map(Entry::gigId).toList());
		Map<UUID, String> posterNames = names.firstNames(shown.stream().map(Entry::posterId).distinct().toList());
		List<ShortlistedItem> items = shown.stream().map(e -> new ShortlistedItem(e.applicationId(), e.shortlistedAt(),
				gigsById.get(e.gigId()), posterNames.get(e.posterId()), e.posterShares(), e.talentConsents(),
				e.talentConsents() ? e.talentConsentedAt() : null)).toList();
		String next = fetched.size() > size
				? new ApplicationCursor(shown.get(size - 1).shortlistedAt(), shown.get(size - 1).applicationId()).encode() : null;
		return new ShortlistedPage(items, next);
	}

	private Map<UUID, Entry> entries(List<StoredApplication> applications) {
		Map<UUID, Entry> entries = new HashMap<>();
		shortlists.findAll(applications.stream().map(StoredApplication::id).toList()).forEach(e -> entries.put(e.applicationId(), e));
		return entries;
	}

	private static String next(List<StoredApplication> fetched, int size) {
		if (fetched.size() <= size) {
			return null;
		}
		StoredApplication last = fetched.get(size - 1);
		return new ApplicationCursor(last.appliedAt(), last.id()).encode();
	}

	private int pageSize(Integer requested) {
		if (requested == null) {
			return properties.defaultPageSize();
		}
		if (requested < 1 || requested > properties.maxPageSize()) {
			throw new InvalidApplicationException("The page size must be between 1 and " + properties.maxPageSize() + ".");
		}
		return requested;
	}

	private static Keyset keyset(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		ApplicationCursor decoded = ApplicationCursor.decode(cursor);
		return new Keyset(decoded.at(), decoded.id());
	}

}
