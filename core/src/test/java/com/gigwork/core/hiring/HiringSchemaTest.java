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

	@AfterEach
	void cleanUp() {
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

}
