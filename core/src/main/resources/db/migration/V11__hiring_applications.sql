-- Hiring: applications (data model section 7, FR-23, NFR-39). Accounts and gigs are referenced by ID only
-- (ADR-0004), so there are no foreign keys out of this schema.

-- One row per Talent per gig for ever: withdrawing and applying again reactivates the same row.
CREATE TABLE hiring.application (
    id                  uuid        PRIMARY KEY,
    gig_id              uuid        NOT NULL,
    talent_id           uuid        NOT NULL,
    -- copied from the gig when the application is first made; a gig's Poster never changes
    poster_id           uuid        NOT NULL,
    note                text,
    status              text        NOT NULL,
    -- the latest submission: the first application, or the last re-apply
    applied_at          timestamptz NOT NULL,
    withdrawn_at        timestamptz,
    busy_warning_shown  boolean     NOT NULL,
    created_at          timestamptz NOT NULL,
    CONSTRAINT application_one_per_talent_and_gig UNIQUE (gig_id, talent_id),
    CONSTRAINT application_status_valid CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    CONSTRAINT application_note_length CHECK (note IS NULL OR (char_length(note) BETWEEN 1 AND 500)),
    CONSTRAINT application_withdrawn_at_matches_status CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL))
);

-- A gig's active applications, oldest first (the Poster's list).
CREATE INDEX application_gig_active ON hiring.application (gig_id, applied_at, id) WHERE status = 'ACTIVE';
-- A Talent's applications, newest first (the dashboard).
CREATE INDEX application_talent ON hiring.application (talent_id, applied_at DESC, id DESC);

-- One row per submission, including every re-apply, so the rolling 24-hour limit counts apply/withdraw loops.
-- Rows are never changed; they are deleted only by retention and by account erasure.
CREATE TABLE hiring.application_submission (
    id              uuid        PRIMARY KEY,
    talent_id       uuid        NOT NULL,
    application_id  uuid        NOT NULL REFERENCES hiring.application (id),
    submitted_at    timestamptz NOT NULL
);
CREATE INDEX application_submission_talent_time ON hiring.application_submission (talent_id, submitted_at);
CREATE INDEX application_submission_application ON hiring.application_submission (application_id);

CREATE FUNCTION hiring.refuse_submission_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'application_submission rows are never updated' USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER application_submission_no_update BEFORE UPDATE ON hiring.application_submission
    FOR EACH ROW EXECUTE FUNCTION hiring.refuse_submission_update();
