-- A city's IANA time zone: all-day busy blocks are read in the Talent's city zone (FR-7). Existing cities are
-- backfilled with the one city we have; the default is then dropped so every new city has to state its zone
-- (the seed loader validates it with java.time.ZoneId).
ALTER TABLE reference.city ADD COLUMN time_zone text NOT NULL DEFAULT 'Asia/Kolkata';
ALTER TABLE reference.city ALTER COLUMN time_zone DROP DEFAULT;
ALTER TABLE reference.city ADD CONSTRAINT city_time_zone_not_blank CHECK (btrim(time_zone) <> '');
