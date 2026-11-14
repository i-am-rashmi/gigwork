-- Reference Data module (data model §4, ADR-0003). Rows are never deleted: status LIVE/RETIRED (a city: LIVE/NOT_LIVE).
-- IDs are deterministic UUIDv5 values computed by the seed loader from stable keys (so every environment has the
-- same IDs); unlike other modules' random IDs, rows here are seeded, not created by users.

CREATE TABLE reference.city (
    id      uuid PRIMARY KEY,
    slug    text NOT NULL,
    name    text NOT NULL,
    status  text NOT NULL CONSTRAINT city_status_valid CHECK (status IN ('LIVE', 'NOT_LIVE')),
    CONSTRAINT city_slug_unique UNIQUE (slug),
    CONSTRAINT city_name_unique UNIQUE (name)
);

CREATE TABLE reference.zone (
    id        uuid PRIMARY KEY,
    city_id   uuid NOT NULL REFERENCES reference.city (id),
    name      text NOT NULL,
    boundary  geometry(MultiPolygon, 4326) NOT NULL,
    status    text NOT NULL DEFAULT 'LIVE' CONSTRAINT zone_status_valid CHECK (status IN ('LIVE', 'RETIRED')),
    CONSTRAINT zone_name_unique_in_city UNIQUE (city_id, name),
    CONSTRAINT zone_boundary_valid CHECK (ST_IsValid(boundary))
);
CREATE INDEX zone_boundary_gist ON reference.zone USING gist (boundary);

CREATE TABLE reference.category (
    id                 uuid PRIMARY KEY,
    slug               text    NOT NULL,
    name               text    NOT NULL,
    work_mode          text    NOT NULL CONSTRAINT category_work_mode_valid CHECK (work_mode IN ('ON_SITE', 'REMOTE', 'BOTH')),
    language_relevant  boolean NOT NULL,
    status             text    NOT NULL DEFAULT 'LIVE' CONSTRAINT category_status_valid CHECK (status IN ('LIVE', 'RETIRED')),
    display_order      integer NOT NULL,
    CONSTRAINT category_slug_unique UNIQUE (slug),
    CONSTRAINT category_name_unique UNIQUE (name)
);

CREATE TABLE reference.specialisation (
    id             uuid PRIMARY KEY,
    category_id    uuid    NOT NULL REFERENCES reference.category (id),
    slug           text    NOT NULL,
    name           text    NOT NULL,
    status         text    NOT NULL DEFAULT 'LIVE' CONSTRAINT specialisation_status_valid CHECK (status IN ('LIVE', 'RETIRED')),
    display_order  integer NOT NULL,
    CONSTRAINT specialisation_slug_unique_in_category UNIQUE (category_id, slug),
    CONSTRAINT specialisation_name_unique_in_category UNIQUE (category_id, name)
);

-- Keyed by its ISO 639 code, which is also the ID other modules store.
CREATE TABLE reference.language (
    code           text PRIMARY KEY CONSTRAINT language_code_format CHECK (code ~ '^[a-z]{2,3}$'),
    name           text    NOT NULL,
    status         text    NOT NULL DEFAULT 'LIVE' CONSTRAINT language_status_valid CHECK (status IN ('LIVE', 'RETIRED')),
    display_order  integer NOT NULL,
    CONSTRAINT language_name_unique UNIQUE (name)
);

-- A search shortcut, never a real location. source_id is the stable key from the seed (infra/seed/README.md).
-- Names are NOT unique: the same name can occur in several places of a city. pin is NULL when unknown.
CREATE TABLE reference.locality (
    id         uuid PRIMARY KEY,
    source_id  text NOT NULL,
    city_id    uuid NOT NULL REFERENCES reference.city (id),
    zone_id    uuid NOT NULL REFERENCES reference.zone (id),
    name       text NOT NULL,
    pin        text CONSTRAINT locality_pin_format CHECK (pin ~ '^[1-9][0-9]{5}$'),
    lat        double precision NOT NULL CONSTRAINT locality_lat_range CHECK (lat BETWEEN -90 AND 90),
    lng        double precision NOT NULL CONSTRAINT locality_lng_range CHECK (lng BETWEEN -180 AND 180),
    location   geometry(Point, 4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)) STORED,
    status     text NOT NULL DEFAULT 'LIVE' CONSTRAINT locality_status_valid CHECK (status IN ('LIVE', 'RETIRED')),
    CONSTRAINT locality_source_unique_in_city UNIQUE (city_id, source_id)
);
CREATE INDEX locality_location_gist ON reference.locality USING gist (location);
CREATE INDEX locality_zone ON reference.locality (zone_id);
