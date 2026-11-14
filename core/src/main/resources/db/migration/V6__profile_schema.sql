-- Talent Profile module (FR-4 to FR-6, FR-8, ADR-0003, ADR-0008). Other modules' data (account, category,
-- specialisation, language, city, zone, locality) is referenced by ID only, with no foreign keys across schemas.
-- A profile is saved in three sections (work, location, portfolio), each replaced as a whole under a lock on the
-- profile row; the status is derived by the database, so it can never disagree with the data.

CREATE TABLE profile.talent_profile (
    account_id         uuid PRIMARY KEY,

    -- work section: all NULL until saved
    profile_type       text
                       CONSTRAINT talent_profile_type_valid CHECK (profile_type IN ('INDIVIDUAL', 'GROUP')),
    member_count       integer,
    headline           text
                       CONSTRAINT talent_profile_headline_length CHECK (char_length(headline) BETWEEN 1 AND 80),
    bio                text
                       CONSTRAINT talent_profile_bio_length CHECK (char_length(bio) <= 1000),

    -- location section: all NULL until saved (coordinates are never returned by any API, NFR-35)
    lat                double precision CONSTRAINT talent_profile_lat_range CHECK (lat BETWEEN -90 AND 90),
    lng                double precision CONSTRAINT talent_profile_lng_range CHECK (lng BETWEEN -180 AND 180),
    location           geography(Point, 4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography) STORED,
    city_id            uuid,
    zone_id            uuid,
    location_label     text,
    locality_id        uuid,    -- only when the Talent chose a locality from the list
    service_radius_km  integer
                       CONSTRAINT talent_profile_radius_range CHECK (service_radius_km BETWEEN 1 AND 50),
    willing_to_travel  boolean,

    hidden             boolean     NOT NULL DEFAULT false,
    -- INCOMPLETE until work and location are both saved, then ACTIVE; HIDDEN while the Talent has hidden it
    status             text GENERATED ALWAYS AS (
                           CASE WHEN hidden THEN 'HIDDEN'
                                WHEN profile_type IS NOT NULL AND lat IS NOT NULL THEN 'ACTIVE'
                                ELSE 'INCOMPLETE' END) STORED,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,

    -- a group may give its size (2-50); an individual never does
    CONSTRAINT talent_profile_member_count CHECK (
        member_count IS NULL OR (profile_type = 'GROUP' AND member_count BETWEEN 2 AND 50)),
    -- each section is saved whole: its columns are all set or all empty
    CONSTRAINT talent_profile_work_whole CHECK ((profile_type IS NULL) = (headline IS NULL)),
    CONSTRAINT talent_profile_location_whole CHECK (
        (lat IS NULL) = (lng IS NULL) AND (lat IS NULL) = (city_id IS NULL) AND (lat IS NULL) = (zone_id IS NULL)
        AND (lat IS NULL) = (location_label IS NULL) AND (lat IS NULL) = (service_radius_km IS NULL)
        AND (lat IS NULL) = (willing_to_travel IS NULL))
);
CREATE INDEX talent_profile_location_gist ON profile.talent_profile USING gist (location);

CREATE TABLE profile.talent_category (
    account_id   uuid NOT NULL REFERENCES profile.talent_profile (account_id) ON DELETE CASCADE,
    category_id  uuid NOT NULL,
    PRIMARY KEY (account_id, category_id)
);

-- Removing a category removes its specialisations in the database. That a specialisation really belongs to
-- the category is checked against Reference Data when saving (another module's data).
CREATE TABLE profile.talent_specialisation (
    account_id         uuid NOT NULL,
    category_id        uuid NOT NULL,
    specialisation_id  uuid NOT NULL,
    PRIMARY KEY (account_id, specialisation_id),
    FOREIGN KEY (account_id, category_id) REFERENCES profile.talent_category (account_id, category_id) ON DELETE CASCADE
);

CREATE TABLE profile.talent_language (
    account_id     uuid NOT NULL REFERENCES profile.talent_profile (account_id) ON DELETE CASCADE,
    language_code  text NOT NULL,
    PRIMARY KEY (account_id, language_code)
);

CREATE TABLE profile.portfolio_link (
    id             uuid PRIMARY KEY,
    account_id     uuid     NOT NULL REFERENCES profile.talent_profile (account_id) ON DELETE CASCADE,
    url            text     NOT NULL CONSTRAINT portfolio_link_url_length CHECK (char_length(url) BETWEEN 1 AND 2048),
    type           text     NOT NULL
                   CONSTRAINT portfolio_link_type_valid
                   CHECK (type IN ('WEBSITE', 'INSTAGRAM', 'YOUTUBE', 'BEHANCE', 'VIMEO', 'AUDIO', 'SOCIAL')),
    display_order  integer  NOT NULL CONSTRAINT portfolio_link_order_range CHECK (display_order BETWEEN 0 AND 9),
    CONSTRAINT portfolio_link_url_unique UNIQUE (account_id, url),
    CONSTRAINT portfolio_link_order_unique UNIQUE (account_id, display_order)
);
