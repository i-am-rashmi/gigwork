-- Hiring: shortlist and consent (data model section 7, FR-25, FR-25a, FR-26, NFR-38). Accounts and gigs are
-- referenced by ID only (ADR-0004).

-- One row per application for ever: removing a Talent and adding them again reuses the row and resets consent.
CREATE TABLE hiring.shortlist_entry (
    application_id        uuid        PRIMARY KEY REFERENCES hiring.application (id),
    gig_id                uuid        NOT NULL,
    poster_id             uuid        NOT NULL,
    talent_id             uuid        NOT NULL,
    shortlisted_at        timestamptz NOT NULL,
    -- set when the Poster removes the Talent, or the application is withdrawn; null while shortlisted
    removed_at            timestamptz,
    -- the Poster's choice to share their own contact with this Talent
    poster_shares         boolean     NOT NULL,
    -- the Talent's consent to share theirs; withdrawn later or reset when added again
    talent_consented_at   timestamptz,
    consent_withdrawn_at  timestamptz,
    CONSTRAINT shortlist_entry_removed_after_shortlisted CHECK (removed_at IS NULL OR removed_at >= shortlisted_at),
    CONSTRAINT shortlist_entry_consent_after_shortlisted CHECK (talent_consented_at IS NULL OR talent_consented_at >= shortlisted_at),
    CONSTRAINT shortlist_entry_withdrawal_needs_consent CHECK (
        consent_withdrawn_at IS NULL
        OR (talent_consented_at IS NOT NULL AND consent_withdrawn_at >= talent_consented_at))
);
-- The Talents currently shortlisted for a gig (the limit of 10, the Poster's list).
CREATE INDEX shortlist_entry_gig_current ON hiring.shortlist_entry (gig_id) WHERE removed_at IS NULL;
-- A Talent's Shortlisted tab, newest first.
CREATE INDEX shortlist_entry_talent_current ON hiring.shortlist_entry (talent_id, shortlisted_at DESC, application_id DESC)
    WHERE removed_at IS NULL;

-- One row per shortlist action of a Poster (adding and removing), for the rolling 24-hour limit. Never changed;
-- deleted only by retention and account erasure.
CREATE TABLE hiring.shortlist_action (
    id              uuid        PRIMARY KEY,
    poster_id       uuid        NOT NULL,
    application_id  uuid        NOT NULL REFERENCES hiring.application (id),
    action          text        NOT NULL,
    at              timestamptz NOT NULL,
    CONSTRAINT shortlist_action_valid CHECK (action IN ('SHORTLIST', 'REMOVE'))
);
CREATE INDEX shortlist_action_poster_time ON hiring.shortlist_action (poster_id, at);

-- Every view of a contact: who looked at whose, for which gig. Kept for 1 year (the retention job comes later).
-- No foreign keys: the log outlives the rows it describes.
CREATE TABLE hiring.contact_access_log (
    id              uuid        PRIMARY KEY,
    viewer_id       uuid        NOT NULL,
    viewed_id       uuid        NOT NULL,
    gig_id          uuid        NOT NULL,
    application_id  uuid        NOT NULL,
    viewed_at       timestamptz NOT NULL
);
CREATE INDEX contact_access_log_viewed_at ON hiring.contact_access_log (viewed_at);

CREATE FUNCTION hiring.refuse_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% rows are never updated', TG_TABLE_NAME USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER shortlist_action_no_update BEFORE UPDATE ON hiring.shortlist_action
    FOR EACH ROW EXECUTE FUNCTION hiring.refuse_update();
CREATE TRIGGER contact_access_log_no_update BEFORE UPDATE ON hiring.contact_access_log
    FOR EACH ROW EXECUTE FUNCTION hiring.refuse_update();
