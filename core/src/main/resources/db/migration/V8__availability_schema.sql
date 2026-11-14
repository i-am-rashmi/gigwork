-- Availability module (FR-7, FR-29, NFR-14, ADR-0002). Accounts and gigs are referenced by ID only.
-- All ranges are half-open [start, end): back-to-back periods (10-14 and 14-18) do not overlap.

-- A Talent's own unavailable times: private hints, used for warnings and matching, never blocking a hire.
-- Busy blocks may overlap each other.
CREATE TABLE availability.busy_block (
    id          uuid PRIMARY KEY,
    account_id  uuid        NOT NULL,
    period      tstzrange   NOT NULL,
    all_day     boolean     NOT NULL,
    -- an all-day block keeps its dates (both inclusive) for display; the period is midnight to midnight in the
    -- Talent's city time zone when it was added
    start_date  date,
    end_date    date,
    created_at  timestamptz NOT NULL,
    CONSTRAINT busy_block_period_valid CHECK (
        NOT isempty(period) AND lower_inc(period) AND NOT upper_inc(period)
        AND NOT lower_inf(period) AND NOT upper_inf(period)),
    CONSTRAINT busy_block_all_day_dates CHECK (
        (all_day AND start_date IS NOT NULL AND end_date IS NOT NULL AND end_date >= start_date)
        OR (NOT all_day AND start_date IS NULL AND end_date IS NULL))
);
CREATE INDEX busy_block_account_period ON availability.busy_block USING gist (account_id, period);

-- The times of a confirmed hire. These must never overlap for one Talent, even under simultaneous requests.
CREATE TABLE availability.booking (
    id            uuid PRIMARY KEY,
    account_id    uuid        NOT NULL,
    gig_id        uuid        NOT NULL,
    period        tstzrange   NOT NULL,
    status        text        NOT NULL DEFAULT 'ACTIVE'
                  CONSTRAINT booking_status_valid CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at    timestamptz NOT NULL,
    cancelled_at  timestamptz,
    CONSTRAINT booking_period_valid CHECK (
        NOT isempty(period) AND lower_inc(period) AND NOT upper_inc(period)
        AND NOT lower_inf(period) AND NOT upper_inf(period)),
    CONSTRAINT booking_cancelled_at_matches_status CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    -- no two ACTIVE bookings of one Talent overlap: the database decides, never check-then-insert
    CONSTRAINT booking_no_overlap EXCLUDE USING gist (account_id WITH =, period WITH &&) WHERE (status = 'ACTIVE')
);
-- a gig has at most one ACTIVE booking
CREATE UNIQUE INDEX booking_one_active_per_gig ON availability.booking (gig_id) WHERE status = 'ACTIVE';
