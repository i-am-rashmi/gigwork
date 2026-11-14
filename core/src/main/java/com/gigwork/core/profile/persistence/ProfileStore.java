package com.gigwork.core.profile.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profiles. Writes enforce an invariant (one list, never a mix of two concurrent saves, ADR-0008), so every write
 * is native SQL and must run inside the caller's transaction after {@link #lockOrCreate}: that call inserts the
 * profile row if it is missing and locks it, so all saves for one Talent are serialised and everything done under
 * the lock (replacing child rows, the list limits checked on the request) sees a stable profile. Reads use JPA.
 */
@Repository
public class ProfileStore {

	public record Work(String profileType, Integer memberCount, String headline, String bio, List<UUID> categoryIds,
			List<StoredProfile.Specialisation> specialisations, List<String> languageCodes) {
	}

	public record Location(double lat, double lng, UUID cityId, UUID zoneId, String label, UUID localityId,
			int serviceRadiusKm, boolean willingToTravel) {
	}

	public record NewLink(UUID id, String url, String type) {
	}

	private final JdbcClient jdbc;
	private final TalentProfileJpaRepository profiles;

	ProfileStore(JdbcClient jdbc, TalentProfileJpaRepository profiles) {
		this.jdbc = jdbc;
		this.profiles = profiles;
	}

	/** Creates the (empty, INCOMPLETE) profile if there is none, then locks it until the transaction ends. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockOrCreate(UUID accountId, Instant now) {
		jdbc.sql("""
				INSERT INTO profile.talent_profile (account_id, created_at, updated_at) VALUES (:id, :now, :now)
				ON CONFLICT (account_id) DO NOTHING""").param("id", accountId).param("now", ts(now)).update();
		jdbc.sql("SELECT account_id FROM profile.talent_profile WHERE account_id = :id FOR UPDATE")
				.param("id", accountId).query(UUID.class).single();
	}

	/** Replaces the whole work section. Deleting the categories also deletes their specialisations (cascade). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void replaceWork(UUID accountId, Work work, Instant now) {
		jdbc.sql("DELETE FROM profile.talent_category WHERE account_id = :id").param("id", accountId).update();
		jdbc.sql("DELETE FROM profile.talent_language WHERE account_id = :id").param("id", accountId).update();
		for (UUID category : work.categoryIds()) {
			jdbc.sql("INSERT INTO profile.talent_category (account_id, category_id) VALUES (:a, :c)")
					.param("a", accountId).param("c", category).update();
		}
		for (StoredProfile.Specialisation s : work.specialisations()) {
			jdbc.sql("INSERT INTO profile.talent_specialisation (account_id, category_id, specialisation_id) VALUES (:a, :c, :s)")
					.param("a", accountId).param("c", s.categoryId()).param("s", s.specialisationId()).update();
		}
		for (String language : work.languageCodes()) {
			jdbc.sql("INSERT INTO profile.talent_language (account_id, language_code) VALUES (:a, :l)")
					.param("a", accountId).param("l", language).update();
		}
		jdbc.sql("""
				UPDATE profile.talent_profile SET profile_type = :type, member_count = :count, headline = :headline,
				    bio = :bio, updated_at = :now WHERE account_id = :id""")
				.param("type", work.profileType()).param("count", work.memberCount()).param("headline", work.headline())
				.param("bio", work.bio()).param("now", ts(now)).param("id", accountId).update();
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void replaceLocation(UUID accountId, Location location, Instant now) {
		jdbc.sql("""
				UPDATE profile.talent_profile SET lat = :lat, lng = :lng, city_id = :city, zone_id = :zone,
				    location_label = :label, locality_id = :locality, service_radius_km = :radius,
				    willing_to_travel = :travel, updated_at = :now WHERE account_id = :id""")
				.param("lat", location.lat()).param("lng", location.lng()).param("city", location.cityId())
				.param("zone", location.zoneId()).param("label", location.label()).param("locality", location.localityId())
				.param("radius", location.serviceRadiusKm()).param("travel", location.willingToTravel())
				.param("now", ts(now)).param("id", accountId).update();
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void replaceLinks(UUID accountId, List<NewLink> links, Instant now) {
		jdbc.sql("DELETE FROM profile.portfolio_link WHERE account_id = :id").param("id", accountId).update();
		int order = 0;
		for (NewLink link : links) {
			jdbc.sql("""
					INSERT INTO profile.portfolio_link (id, account_id, url, type, display_order)
					VALUES (:id, :a, :url, :type, :order)""")
					.param("id", link.id()).param("a", accountId).param("url", link.url()).param("type", link.type())
					.param("order", order++).update();
		}
		touch(accountId, now);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void setHidden(UUID accountId, boolean hidden, Instant now) {
		jdbc.sql("UPDATE profile.talent_profile SET hidden = :h, updated_at = :now WHERE account_id = :id")
				.param("h", hidden).param("now", ts(now)).param("id", accountId).update();
	}

	/** JdbcClient needs a type PostgreSQL's driver can map: timestamptz is an OffsetDateTime, not an Instant. */
	private static OffsetDateTime ts(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private void touch(UUID accountId, Instant now) {
		jdbc.sql("UPDATE profile.talent_profile SET updated_at = :now WHERE account_id = :id").param("now", ts(now))
				.param("id", accountId).update();
	}

	@Transactional(readOnly = true)
	public Optional<StoredProfile> find(UUID accountId) {
		return profiles.findById(accountId).map(TalentProfile::toStored);
	}

}
