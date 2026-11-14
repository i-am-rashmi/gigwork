-- Gigs (data model section 5, FR-15, FR-16, FR-18, FR-19, FR-31). Other modules' data is referenced by ID only
-- (ADR-0004), so there are no foreign keys out of this schema.
--
-- Only rules the database can hold are constraints here. "Starts in the future", "at most a year ahead" and "at most
-- 14 days long" depend on the current time or on settings, so the application checks them when it writes.
CREATE TABLE gigs.gig (
    id                uuid             PRIMARY KEY,
    poster_id         uuid             NOT NULL,
    category_id       uuid             NOT NULL,
    specialisation_id uuid,
    language_code     text,
    title             text             NOT NULL,
    description       text             NOT NULL,
    start_at          timestamptz      NOT NULL,
    end_at            timestamptz      NOT NULL,
    work_mode         text             NOT NULL,
    -- on-site gigs only: where, as chosen through Reference's location input (never returned by any API)
    lat               double precision,
    lng               double precision,
    location          geography(Point, 4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography) STORED,
    city_id           uuid,
    zone_id           uuid,
    location_label    text,
    locality_id       uuid,
    ta_offered        boolean,
    -- whole rupees; both or neither
    budget_min        bigint,
    budget_max        bigint,
    status            text             NOT NULL,
    published_at      timestamptz      NOT NULL,
    updated_at        timestamptz      NOT NULL,
    closed_at         timestamptz,
    close_reason      text,

    CONSTRAINT gig_title_length        CHECK (char_length(title) BETWEEN 1 AND 100),
    CONSTRAINT gig_description_length  CHECK (char_length(description) BETWEEN 1 AND 2000),
    CONSTRAINT gig_language_length     CHECK (language_code IS NULL OR char_length(language_code) BETWEEN 1 AND 8),
    CONSTRAINT gig_ends_after_start    CHECK (end_at > start_at),
    CONSTRAINT gig_work_mode_valid     CHECK (work_mode IN ('ON_SITE', 'REMOTE')),
    CONSTRAINT gig_status_valid        CHECK (status IN ('OPEN', 'FILLED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT gig_lat_range           CHECK (lat IS NULL OR lat BETWEEN -90 AND 90),
    CONSTRAINT gig_lng_range           CHECK (lng IS NULL OR lng BETWEEN -180 AND 180),
    -- on-site <=> a location and the TA flag; remote <=> none of them
    CONSTRAINT gig_location_matches_work_mode CHECK (
        (work_mode = 'ON_SITE' AND lat IS NOT NULL AND lng IS NOT NULL AND city_id IS NOT NULL
            AND zone_id IS NOT NULL AND location_label IS NOT NULL AND ta_offered IS NOT NULL)
        OR
        (work_mode = 'REMOTE' AND lat IS NULL AND lng IS NULL AND city_id IS NULL AND zone_id IS NULL
            AND location_label IS NULL AND locality_id IS NULL AND ta_offered IS NULL)),
    CONSTRAINT gig_location_label_length CHECK (location_label IS NULL OR char_length(location_label) BETWEEN 1 AND 200),
    CONSTRAINT gig_budget_whole       CHECK ((budget_min IS NULL) = (budget_max IS NULL)),
    CONSTRAINT gig_budget_not_negative CHECK (budget_min IS NULL OR (budget_min >= 0 AND budget_max >= 0)),
    CONSTRAINT gig_budget_ordered     CHECK (budget_min IS NULL OR budget_min <= budget_max),
    -- closed_at is set exactly when the gig is cancelled or expired; a filled gig can still reopen
    CONSTRAINT gig_closed_at_matches_status CHECK ((closed_at IS NOT NULL) = (status IN ('CANCELLED', 'EXPIRED'))),
    CONSTRAINT gig_close_reason_only_when_cancelled CHECK (
        close_reason IS NULL OR (status = 'CANCELLED' AND char_length(close_reason) BETWEEN 1 AND 500))
);

-- Browsing (FR-9): open gigs, newest first, keyset on (published_at, id). Every index below is partial on the rows
-- that are open (data model: only a small fraction is open at any time), so it does not grow with closed gigs.

-- The list without filters, and any page that no narrower index serves better.
CREATE INDEX gig_open_newest ON gigs.gig (published_at DESC, id DESC) WHERE status = 'OPEN';

-- The city filter (and zones within it). A remote gig has no city, so it is read through gig_open_remote.
CREATE INDEX gig_open_city ON gigs.gig (city_id, published_at DESC, id DESC) WHERE status = 'OPEN';

-- Remote gigs are always included whatever the location filter says.
CREATE INDEX gig_open_remote ON gigs.gig (published_at DESC, id DESC) WHERE status = 'OPEN' AND work_mode = 'REMOTE';

-- The category filter.
CREATE INDEX gig_open_category ON gigs.gig (category_id, published_at DESC, id DESC) WHERE status = 'OPEN';

-- The expiry sweep (ADR-0010): open gigs whose start has passed, oldest start first.
CREATE INDEX gig_open_start ON gigs.gig (start_at) WHERE status = 'OPEN';

-- A Poster's own gigs, all statuses, newest first.
CREATE INDEX gig_by_poster ON gigs.gig (poster_id, published_at DESC, id DESC);
