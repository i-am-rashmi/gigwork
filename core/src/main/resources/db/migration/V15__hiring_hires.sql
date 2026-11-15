-- Hiring: hires (data model section 7, FR-27, FR-27a, FR-28, FR-29). Accounts and gigs are referenced by ID only
-- (ADR-0004); booking_id points into Availability by ID.
CREATE TABLE hiring.hire (
    id              uuid        PRIMARY KEY,
    gig_id          uuid        NOT NULL,
    application_id  uuid        NOT NULL REFERENCES hiring.application (id),
    talent_id       uuid        NOT NULL,
    poster_id       uuid        NOT NULL,
    started_by      text        NOT NULL,
    status          text        NOT NULL,
    started_at      timestamptz NOT NULL,
    -- the earlier of 24 hours after starting and the gig's start; a pending hire past it is lapsed
    expires_at      timestamptz NOT NULL,
    confirmed_at    timestamptz,
    -- when a hire that is no longer pending or confirmed ended (lapsed, declined, withdrawn, cancelled)
    ended_at        timestamptz,
    cancelled_by    text,
    cancel_reason   text,
    booking_id      uuid,
    CONSTRAINT hire_started_by_valid CHECK (started_by IN ('POSTER', 'TALENT')),
    CONSTRAINT hire_status_valid CHECK (status IN ('PENDING', 'CONFIRMED', 'LAPSED', 'DECLINED', 'WITHDRAWN', 'CANCELLED')),
    CONSTRAINT hire_expires_after_start CHECK (expires_at > started_at),
    CONSTRAINT hire_cancelled_by_valid CHECK (cancelled_by IS NULL OR cancelled_by IN ('POSTER', 'TALENT')),
    CONSTRAINT hire_cancel_reason_length CHECK (cancel_reason IS NULL OR char_length(cancel_reason) BETWEEN 1 AND 500),
    -- each status says which of the other columns are set
    CONSTRAINT hire_pending_shape CHECK (status <> 'PENDING' OR (
        confirmed_at IS NULL AND ended_at IS NULL AND cancelled_by IS NULL AND cancel_reason IS NULL AND booking_id IS NULL)),
    CONSTRAINT hire_confirmed_shape CHECK (status <> 'CONFIRMED' OR (
        confirmed_at IS NOT NULL AND booking_id IS NOT NULL AND ended_at IS NULL AND cancelled_by IS NULL AND cancel_reason IS NULL)),
    CONSTRAINT hire_not_confirmed_shape CHECK (status NOT IN ('LAPSED', 'DECLINED', 'WITHDRAWN') OR (
        ended_at IS NOT NULL AND confirmed_at IS NULL AND booking_id IS NULL AND cancelled_by IS NULL AND cancel_reason IS NULL)),
    CONSTRAINT hire_cancelled_shape CHECK (status <> 'CANCELLED' OR (
        confirmed_at IS NOT NULL AND ended_at IS NOT NULL AND cancelled_by IS NOT NULL)),
    CONSTRAINT hire_ended_after_started CHECK (ended_at IS NULL OR ended_at >= started_at)
);

-- A gig can have only one pending hire, and only one confirmed hire (FR-27).
CREATE UNIQUE INDEX hire_one_pending_per_gig ON hiring.hire (gig_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX hire_one_confirmed_per_gig ON hiring.hire (gig_id) WHERE status = 'CONFIRMED';
-- The lapse sweep (ADR-0010): pending hires past their expiry, oldest first.
CREATE INDEX hire_pending_expiry ON hiring.hire (expires_at) WHERE status = 'PENDING';
-- The latest hire of an application, for the lists.
CREATE INDEX hire_by_application ON hiring.hire (application_id, started_at DESC);
