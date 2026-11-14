package com.gigwork.core.gigs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.gigwork.core.reference.support.ReferenceIntegrationTest;

/** What the database itself guarantees about a gig (data model section 5): every CHECK, and the partial indexes. */
@ReferenceIntegrationTest
class GigSchemaTest {

	private static final UUID POSTER = UUID.randomUUID();

	@Autowired
	JdbcClient jdbc;
	@Autowired
	TransactionTemplate transaction;

	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM gigs.gig WHERE poster_id = :p").param("p", POSTER).update();
	}

	private static Map<String, Object> onSite() {
		OffsetDateTime start = OffsetDateTime.now().plusDays(3);
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", UUID.randomUUID());
		row.put("poster_id", POSTER);
		row.put("category_id", UUID.randomUUID());
		row.put("specialisation_id", null);
		row.put("language_code", null);
		row.put("title", "A gig");
		row.put("description", "A description");
		row.put("start_at", start);
		row.put("end_at", start.plusHours(4));
		row.put("work_mode", "ON_SITE");
		row.put("lat", 12.97);
		row.put("lng", 77.6);
		row.put("city_id", UUID.randomUUID());
		row.put("zone_id", UUID.randomUUID());
		row.put("location_label", "Indiranagar");
		row.put("locality_id", null);
		row.put("ta_offered", true);
		row.put("budget_min", null);
		row.put("budget_max", null);
		row.put("status", "OPEN");
		row.put("published_at", OffsetDateTime.now());
		row.put("updated_at", OffsetDateTime.now());
		row.put("closed_at", null);
		row.put("close_reason", null);
		return row;
	}

	private static Map<String, Object> remote() {
		Map<String, Object> row = onSite();
		row.put("work_mode", "REMOTE");
		for (String column : List.of("lat", "lng", "city_id", "zone_id", "location_label", "locality_id", "ta_offered")) {
			row.put(column, null);
		}
		return row;
	}

	private static Map<String, Object> with(Map<String, Object> row, Object... overrides) {
		for (int i = 0; i < overrides.length; i += 2) {
			row.put((String) overrides[i], overrides[i + 1]);
		}
		return row;
	}

	private void insert(Map<String, Object> row) {
		String columns = String.join(", ", row.keySet());
		String values = row.keySet().stream().map(c -> ":" + c).collect(java.util.stream.Collectors.joining(", "));
		jdbc.sql("INSERT INTO gigs.gig (" + columns + ") VALUES (" + values + ")").params(row).update();
	}

	private void assertRefused(Map<String, Object> row, String constraint) {
		assertThatThrownBy(() -> insert(row)).hasMessageContaining(constraint);
	}

	// ---- valid gigs ------------------------------------------------------------------------------------------------

	@Test
	void anOnSiteAndARemoteGigAreAccepted() {
		insert(onSite());
		insert(remote());
		insert(with(onSite(), "budget_min", 0L, "budget_max", 0L));
		insert(with(onSite(), "budget_min", 500L, "budget_max", 500L));
	}

	@Test
	void theGeneratedPointFollowsLatAndLngAndIsAbsentForRemoteGigs() {
		Map<String, Object> located = onSite();
		Map<String, Object> remote = remote();
		insert(located);
		insert(remote);

		assertThat(jdbc.sql("SELECT ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lng FROM gigs.gig WHERE id = :id")
				.param("id", located.get("id")).query().singleRow()).containsEntry("lat", 12.97).containsEntry("lng", 77.6);
		assertThat(jdbc.sql("SELECT location IS NULL FROM gigs.gig WHERE id = :id").param("id", remote.get("id"))
				.query(Boolean.class).single()).isTrue();
	}

	@Test
	void everyStatusIsAcceptedWithItsClosedAt() {
		OffsetDateTime now = OffsetDateTime.now();
		insert(with(onSite(), "status", "FILLED"));
		insert(with(onSite(), "status", "CANCELLED", "closed_at", now, "close_reason", "Changed plans"));
		insert(with(onSite(), "status", "CANCELLED", "closed_at", now));
		insert(with(onSite(), "status", "EXPIRED", "closed_at", now));
	}

	// ---- text and dates --------------------------------------------------------------------------------------------

	@Test
	void titleAndDescriptionLengthsAreBounded() {
		assertRefused(with(onSite(), "title", ""), "gig_title_length");
		assertRefused(with(onSite(), "title", "x".repeat(101)), "gig_title_length");
		assertRefused(with(onSite(), "description", ""), "gig_description_length");
		assertRefused(with(onSite(), "description", "x".repeat(2001)), "gig_description_length");
		assertRefused(with(onSite(), "language_code", ""), "gig_language_length");
		insert(with(onSite(), "title", "x".repeat(100), "description", "y".repeat(2000)));
	}

	@Test
	void aGigMustEndAfterItStarts() {
		OffsetDateTime start = OffsetDateTime.now().plusDays(1);
		assertRefused(with(onSite(), "start_at", start, "end_at", start), "gig_ends_after_start");
		assertRefused(with(onSite(), "start_at", start, "end_at", start.minusHours(1)), "gig_ends_after_start");
	}

	@Test
	void workModeAndStatusMustBeKnownValues() {
		// an unknown mode also breaks the location rule, and PostgreSQL names only one of the two
		assertThatThrownBy(() -> insert(with(onSite(), "work_mode", "HYBRID")))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		assertRefused(with(onSite(), "status", "DRAFT"), "gig_status_valid");
	}

	// ---- location --------------------------------------------------------------------------------------------------

	@Test
	void coordinatesMustBeOnEarth() {
		assertRefused(with(onSite(), "lat", 91.0), "gig_lat_range");
		assertRefused(with(onSite(), "lng", -181.0), "gig_lng_range");
	}

	@Test
	void anOnSiteGigNeedsEveryLocationColumnAndTheTravelAllowanceFlag() {
		for (String column : List.of("lat", "lng", "city_id", "zone_id", "location_label", "ta_offered")) {
			assertRefused(with(onSite(), column, null), "gig_location_matches_work_mode");
		}
	}

	@Test
	void aRemoteGigHasNoLocationColumnAndNoTravelAllowance() {
		assertRefused(with(remote(), "lat", 12.97, "lng", 77.6), "gig_location_matches_work_mode");
		assertRefused(with(remote(), "city_id", UUID.randomUUID()), "gig_location_matches_work_mode");
		assertRefused(with(remote(), "zone_id", UUID.randomUUID()), "gig_location_matches_work_mode");
		assertRefused(with(remote(), "location_label", "Somewhere"), "gig_location_matches_work_mode");
		assertRefused(with(remote(), "locality_id", UUID.randomUUID()), "gig_location_matches_work_mode");
		assertRefused(with(remote(), "ta_offered", false), "gig_location_matches_work_mode");
	}

	// ---- budget ----------------------------------------------------------------------------------------------------

	@Test
	void aBudgetIsBothOrNeitherNotNegativeAndOrdered() {
		assertRefused(with(onSite(), "budget_min", 100L), "gig_budget_whole");
		assertRefused(with(onSite(), "budget_max", 100L), "gig_budget_whole");
		assertRefused(with(onSite(), "budget_min", -1L, "budget_max", 100L), "gig_budget_not_negative");
		assertRefused(with(onSite(), "budget_min", 0L, "budget_max", -5L), "gig_budget_not_negative");
		assertRefused(with(onSite(), "budget_min", 200L, "budget_max", 100L), "gig_budget_ordered");
	}

	// ---- closing ---------------------------------------------------------------------------------------------------

	@Test
	void closedAtIsSetExactlyWhenTheGigIsCancelledOrExpired() {
		OffsetDateTime now = OffsetDateTime.now();
		assertRefused(with(onSite(), "closed_at", now), "gig_closed_at_matches_status");
		assertRefused(with(onSite(), "status", "FILLED", "closed_at", now), "gig_closed_at_matches_status");
		assertRefused(with(onSite(), "status", "CANCELLED"), "gig_closed_at_matches_status");
		assertRefused(with(onSite(), "status", "EXPIRED"), "gig_closed_at_matches_status");
	}

	@Test
	void aCloseReasonBelongsToACancelledGigAndIsBounded() {
		OffsetDateTime now = OffsetDateTime.now();
		assertRefused(with(onSite(), "close_reason", "why"), "gig_close_reason_only_when_cancelled");
		assertRefused(with(onSite(), "status", "EXPIRED", "closed_at", now, "close_reason", "why"), "gig_close_reason_only_when_cancelled");
		assertRefused(with(onSite(), "status", "CANCELLED", "closed_at", now, "close_reason", ""), "gig_close_reason_only_when_cancelled");
		assertRefused(with(onSite(), "status", "CANCELLED", "closed_at", now, "close_reason", "r".repeat(501)), "gig_close_reason_only_when_cancelled");
		insert(with(onSite(), "status", "CANCELLED", "closed_at", now, "close_reason", "r".repeat(500)));
	}

	// ---- indexes ---------------------------------------------------------------------------------------------------

	@Test
	void theOpenGigIndexesArePartialOnOpenRowsAndThePosterIndexCoversAllStatuses() {
		Map<String, String> definitions = new java.util.HashMap<>();
		jdbc.sql("SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = 'gigs' AND tablename = 'gig'").query(rs -> {
			definitions.put(rs.getString("indexname"), rs.getString("indexdef"));
		});

		for (String open : List.of("gig_open_newest", "gig_open_city", "gig_open_remote", "gig_open_category", "gig_open_start")) {
			assertThat(definitions.get(open)).as(open).contains("status = 'OPEN'::text");
		}
		assertThat(definitions.get("gig_open_remote")).contains("work_mode = 'REMOTE'");
		assertThat(definitions.get("gig_by_poster")).doesNotContain("WHERE");
	}

	/**
	 * With a table where about one gig in ten is open, each query of the open-gig list, its filters, the expiry sweep
	 * and the Poster's list can be answered from its own index, in the order it asks for (no sort). Sequential and bitmap scans
	 * are switched off for the plan so that this checks what the indexes can serve, not what the planner prefers on
	 * a small table.
	 */
	@Test
	void everyOpenGigQueryCanBeServedFromItsIndexInOrder() {
		UUID city = UUID.randomUUID();
		UUID category = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO gigs.gig (id, poster_id, category_id, title, description, start_at, end_at, work_mode, lat, lng,
				    city_id, zone_id, location_label, ta_offered, status, published_at, updated_at, closed_at)
				SELECT gen_random_uuid(), :poster, CASE WHEN n % 20 = 0 THEN :category ELSE gen_random_uuid() END, 't', 'd',
				    now() + interval '3 days', now() + interval '4 days', 'ON_SITE', 12.97, 77.6,
				    CASE WHEN n % 200 = 0 THEN :city ELSE gen_random_uuid() END, gen_random_uuid(), 'x', true,
				    CASE WHEN n % 10 = 0 THEN 'OPEN' ELSE 'EXPIRED' END, now() - n * interval '1 minute', now(),
				    CASE WHEN n % 10 = 0 THEN NULL ELSE now() END
				FROM generate_series(1, 6000) n""").param("poster", POSTER).param("city", city).param("category", category).update();
		jdbc.sql("""
				INSERT INTO gigs.gig (id, poster_id, category_id, title, description, start_at, end_at, work_mode, status,
				    published_at, updated_at)
				SELECT gen_random_uuid(), :poster, gen_random_uuid(), 't', 'd', now() + interval '3 days', now() + interval '4 days',
				    'REMOTE', 'OPEN', now() - n * interval '1 minute', now()
				FROM generate_series(1, 200) n""").param("poster", POSTER).update();
		jdbc.sql("ANALYZE gigs.gig").update();

		assertPlan("SELECT id FROM gigs.gig WHERE status = 'OPEN' ORDER BY published_at DESC, id DESC LIMIT 20", "gig_open_newest");
		assertPlan("SELECT id FROM gigs.gig WHERE status = 'OPEN' AND (published_at, id) < (now(), gen_random_uuid()) "
				+ "ORDER BY published_at DESC, id DESC LIMIT 20", "gig_open_newest");
		assertPlan("SELECT id FROM gigs.gig WHERE status = 'OPEN' AND city_id = '" + city + "' ORDER BY published_at DESC, id DESC LIMIT 20",
				"gig_open_city");
		assertPlan("SELECT id FROM gigs.gig WHERE status = 'OPEN' AND work_mode = 'REMOTE' ORDER BY published_at DESC, id DESC LIMIT 20",
				"gig_open_remote");
		assertPlan("SELECT id FROM gigs.gig WHERE status = 'OPEN' AND category_id = '" + category + "' ORDER BY published_at DESC, id DESC LIMIT 20",
				"gig_open_category");
		assertPlan("SELECT id FROM gigs.gig WHERE status = 'OPEN' AND start_at <= now() + interval '10 days' ORDER BY start_at LIMIT 500",
				"gig_open_start");
		assertPlan("SELECT id FROM gigs.gig WHERE poster_id = '" + POSTER + "' ORDER BY published_at DESC, id DESC LIMIT 20", "gig_by_poster");
	}

	private void assertPlan(String sql, String index) {
		List<String> plan = transaction.execute(status -> {
			jdbc.sql("SET LOCAL enable_seqscan = off").update();
			jdbc.sql("SET LOCAL enable_bitmapscan = off").update(); // a bitmap scan plus a sort is also right for 30 rows
			return jdbc.sql("EXPLAIN " + sql).query(String.class).list();
		});
		String text = String.join("\n", plan);
		assertThat(text).as(sql).contains(index);
		assertThat(text).as("no sort needed: " + sql).doesNotContain("Sort");
	}

	@Test
	void theDatabaseAcceptsThePartialIndexPredicateInInsertsWithoutErrors() {
		assertThatCode(() -> insert(onSite())).doesNotThrowAnyException();
	}

}
