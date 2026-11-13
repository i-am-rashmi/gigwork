-- Spring Modulith event publication registry (ADR-0004):
-- events between core modules are recorded here in the same transaction
-- and marked complete once their listener succeeds.
-- The last three columns are used by newer Spring Modulith versions; they are
-- nullable, so the table also works with older versions.

CREATE TABLE IF NOT EXISTS event_publication (
    id                      UUID        NOT NULL PRIMARY KEY,
    listener_id             TEXT        NOT NULL,
    event_type              TEXT        NOT NULL,
    serialized_event        TEXT        NOT NULL,
    publication_date        TIMESTAMPTZ NOT NULL,
    completion_date         TIMESTAMPTZ,
    status                  TEXT,
    completion_attempts     INT,
    last_resubmission_date  TIMESTAMPTZ
);

-- Finding incomplete publications (for resubmission) quickly
CREATE INDEX IF NOT EXISTS event_publication_incomplete_idx
    ON event_publication (publication_date)
    WHERE completion_date IS NULL;