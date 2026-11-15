package com.gigwork.core.hiring.application;

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
import com.gigwork.core.profile.ApplicantProfile;
import com.gigwork.core.profile.TalentProfiles;

/**
 * The two lists of applications. Each page is one query for the applications plus one batch call to the module
 * that owns the rest (gigs, or profiles), never a call per row.
 */
@Service
public class ApplicationQueries {

	private final ApplicationStore store;
	private final GigLookup gigs;
	private final TalentProfiles profiles;
	private final HiringProperties properties;

	ApplicationQueries(ApplicationStore store, GigLookup gigs, TalentProfiles profiles, HiringProperties properties) {
		this.store = store;
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
		List<MyApplication> items = shown.stream().map(a -> new MyApplication(a.id(), a.gigId(), a.status(), a.note(),
				a.appliedAt(), a.withdrawnAt(), gigsById.get(a.gigId()))).toList();
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
		List<Applicant> items = shown.stream().map(a -> new Applicant(a.id(), a.talentId(), a.note(), a.appliedAt(),
				profilesById.get(a.talentId()))).toList();
		return new ApplicantsPage(items, next(fetched, size));
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
		return new Keyset(decoded.appliedAt(), decoded.id());
	}

}
