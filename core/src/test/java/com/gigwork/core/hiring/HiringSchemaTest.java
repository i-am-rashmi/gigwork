package com.gigwork.core.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.gigwork.core.reference.support.ReferenceIntegrationTest;

/** What the database itself guarantees about applications (data model section 7): each test breaks a rule with SQL. */
@ReferenceIntegrationTest
class HiringSchemaTest {

	@Autowired
	JdbcClient jdbc;

	private final UUID talent = UUID.randomUUID();
	private final UUID gig = UUID.randomUUID();
	private final UUID poster = UUID.randomUUID();

	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM hiring.shortlist_action WHERE poster_id = :t").param("t", poster).update();
		jdbc.sql("DELETE FROM hiring.shortlist_entry WHERE poster_id = :t").param("t", poster).update();
		jdbc.sql("DELETE FROM hiring.contact_access_log WHERE viewer_id = :t").param("t", poster).update();
		jdbc.sql("DELETE FROM hiring.application_submission WHERE talent_id = :t").param("t", talent).update();
		jdbc.sql("DELETE FROM hiring.application WHERE talent_id = :t").param("t", talent).update();
	}

	private int application(UUID id, UUID gigId, String note, String status, boolean withdrawnAt) {
		return jdbc.sql("""
				INSERT INTO hiring.application (id, gig_id, talent_id, poster_id, note, status, applied_at, withdrawn_at,
				    busy_warning_shown, created_at)
				VALUES (:id, :g, :t, gen_random_uuid(), :note, :status, now(), CASE WHEN :w THEN now() END, false, now())""")
				.param("id", id).param("g", gigId).param("t", talent).param("note", note).param("status", status)
				.param("w", withdrawnAt).update();
	}

	private int submission(UUID applicationId) {
		return jdbc.sql("INSERT INTO hiring.application_submission (id, talent_id, application_id, submitted_at) "
				+ "VALUES (gen_random_uuid(), :t, :a, now())").param("t", talent).param("a", applicationId).update();
	}

	@Test
	void aTalentHasOneApplicationPerGig() {
		assertThat(application(UUID.randomUUID(), gig, null, "ACTIVE", false)).isEqualTo(1);
		assertThatThrownBy(() -> application(UUID.randomUUID(), gig, null, "ACTIVE", false))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatCode(() -> application(UUID.randomUUID(), UUID.randomUUID(), null, "ACTIVE", false)).doesNotThrowAnyException();
	}

	@Test
	void theStatusIsActiveOrWithdrawn() {
		assertThatThrownBy(() -> application(UUID.randomUUID(), gig, null, "HIRED", false))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void withdrawnAtIsSetExactlyWhenTheApplicationIsWithdrawn() {
		assertThatThrownBy(() -> application(UUID.randomUUID(), gig, null, "WITHDRAWN", false))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> application(UUID.randomUUID(), gig, null, "ACTIVE", true))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(application(UUID.randomUUID(), gig, null, "WITHDRAWN", true)).isEqualTo(1);
	}

	@Test
	void aNoteHasAtMost500CharactersAndIsNeverEmpty() {
		assertThat(application(UUID.randomUUID(), gig, "x".repeat(500), "ACTIVE", false)).isEqualTo(1);
		assertThatThrownBy(() -> application(UUID.randomUUID(), UUID.randomUUID(), "x".repeat(501), "ACTIVE", false))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> application(UUID.randomUUID(), UUID.randomUUID(), "", "ACTIVE", false))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aSubmissionBelongsToAnApplicationAndIsNeverUpdatedButCanBeDeleted() {
		UUID application = UUID.randomUUID();
		application(application, gig, null, "ACTIVE", false);
		assertThat(submission(application)).isEqualTo(1);
		assertThatThrownBy(() -> submission(UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.sql("UPDATE hiring.application_submission SET submitted_at = now() WHERE talent_id = :t")
				.param("t", talent).update()).isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("never updated");
		assertThat(jdbc.sql("DELETE FROM hiring.application_submission WHERE talent_id = :t").param("t", talent).update())
				.isEqualTo(1);
	}

	@Test
	void theListsAreServedByTheirIndexes() {
		assertThat(jdbc.sql("SELECT indexdef FROM pg_indexes WHERE schemaname = 'hiring' AND indexname = 'application_gig_active'")
				.query(String.class).single()).contains("(gig_id, applied_at, id)").contains("status = 'ACTIVE'");
		assertThat(jdbc.sql("SELECT indexdef FROM pg_indexes WHERE schemaname = 'hiring' AND indexname = 'application_talent'")
				.query(String.class).single()).contains("(talent_id, applied_at DESC, id DESC)");
		assertThat(jdbc.sql("SELECT indexdef FROM pg_indexes WHERE schemaname = 'hiring' AND indexname = 'application_submission_talent_time'")
				.query(String.class).single()).contains("(talent_id, submitted_at)");
	}

	// ---- shortlist and contact log -------------------------------------------------------------------------------------------

	private UUID anApplication() {
		UUID id = UUID.randomUUID();
		application(id, UUID.randomUUID(), null, "ACTIVE", false);
		return id;
	}

	private int entry(UUID applicationId, String shortlisted, String removed, String consented, String withdrawn) {
		return jdbc.sql("""
				INSERT INTO hiring.shortlist_entry (application_id, gig_id, poster_id, talent_id, shortlisted_at, removed_at,
				    poster_shares, talent_consented_at, consent_withdrawn_at)
				VALUES (:a, :g, :p, :t, CAST(:s AS timestamptz), CAST(:r AS timestamptz), true, CAST(:c AS timestamptz),
				    CAST(:w AS timestamptz))""")
				.param("a", applicationId).param("g", gig).param("p", poster).param("t", talent).param("s", shortlisted)
				.param("r", removed).param("c", consented).param("w", withdrawn).update();
	}

	@Test
	void aShortlistEntryBelongsToOneApplicationAndHasOneRowForEver() {
		UUID application = anApplication();
		assertThat(entry(application, "2030-01-01 10:00+00", null, null, null)).isEqualTo(1);
		assertThatThrownBy(() -> entry(application, "2030-01-01 11:00+00", null, null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> entry(UUID.randomUUID(), "2030-01-01 10:00+00", null, null, null))
				.isInstanceOf(DataIntegrityViolationException.class); // no such application
	}

	@Test
	void theTimesOfAShortlistEntryAreCoherent() {
		// removal, consent before the Talent was shortlisted
		assertThatThrownBy(() -> entry(anApplication(), "2030-01-01 10:00+00", "2030-01-01 09:00+00", null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> entry(anApplication(), "2030-01-01 10:00+00", null, "2030-01-01 09:00+00", null))
				.isInstanceOf(DataIntegrityViolationException.class);
		// a withdrawal needs a consent, and cannot come before it
		assertThatThrownBy(() -> entry(anApplication(), "2030-01-01 10:00+00", null, null, "2030-01-01 12:00+00"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> entry(anApplication(), "2030-01-01 10:00+00", null, "2030-01-01 12:00+00", "2030-01-01 11:00+00"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(entry(anApplication(), "2030-01-01 10:00+00", "2030-01-01 13:00+00", "2030-01-01 11:00+00", "2030-01-01 12:00+00"))
				.isEqualTo(1);
	}

	@Test
	void shortlistActionsAndContactViewsAreNeverUpdatedButCanBeDeleted() {
		UUID application = anApplication();
		jdbc.sql("INSERT INTO hiring.shortlist_action (id, poster_id, application_id, action, at) VALUES (gen_random_uuid(), :p, :a, 'SHORTLIST', now())")
				.param("p", poster).param("a", application).update();
		assertThatThrownBy(() -> jdbc.sql("INSERT INTO hiring.shortlist_action (id, poster_id, application_id, action, at) VALUES (gen_random_uuid(), :p, :a, 'HIRE', now())")
				.param("p", poster).param("a", application).update()).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.sql("UPDATE hiring.shortlist_action SET action = 'REMOVE' WHERE poster_id = :p").param("p", poster).update())
				.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("never updated");

		jdbc.sql("INSERT INTO hiring.contact_access_log (id, viewer_id, viewed_id, gig_id, application_id, viewed_at) VALUES (gen_random_uuid(), :p, :t, :g, :a, now())")
				.param("p", poster).param("t", talent).param("g", gig).param("a", application).update();
		assertThatThrownBy(() -> jdbc.sql("UPDATE hiring.contact_access_log SET viewed_at = now() WHERE viewer_id = :p").param("p", poster).update())
				.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("never updated");
		assertThat(jdbc.sql("DELETE FROM hiring.contact_access_log WHERE viewer_id = :p").param("p", poster).update()).isEqualTo(1);
	}

	@Test
	void theShortlistQueriesAreServedByTheirIndexes() {
		for (String name : new String[] { "shortlist_entry_gig_current", "shortlist_entry_talent_current", "shortlist_action_poster_time",
				"contact_access_log_viewed_at" }) {
			assertThat(jdbc.sql("SELECT count(*) FROM pg_indexes WHERE schemaname = 'hiring' AND indexname = :n").param("n", name)
					.query(Integer.class).single()).as(name).isEqualTo(1);
		}
		assertThat(jdbc.sql("SELECT indexdef FROM pg_indexes WHERE indexname = 'shortlist_entry_gig_current'").query(String.class).single())
				.contains("removed_at IS NULL");
	}

}
