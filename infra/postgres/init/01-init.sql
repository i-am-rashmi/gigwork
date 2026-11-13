-- Runs once, as the postgres superuser, the first time the container starts
-- with an empty data volume (ADR-0003: one instance, a database per service).
-- Local development passwords only.

-- One database user per service
CREATE USER core_app   WITH PASSWORD 'core_app';
CREATE USER worker_app WITH PASSWORD 'worker_app';

-- One database per service, owned by its user
CREATE DATABASE gigwork_core   OWNER core_app;
CREATE DATABASE gigwork_worker OWNER worker_app;

-- Each service can connect only to its own database
REVOKE CONNECT ON DATABASE gigwork_core   FROM PUBLIC;
REVOKE CONNECT ON DATABASE gigwork_worker FROM PUBLIC;
GRANT  CONNECT ON DATABASE gigwork_core   TO core_app;
GRANT  CONNECT ON DATABASE gigwork_worker TO worker_app;

-- Extensions need superuser rights, so they are created here (ADR-0002):
--   postgis    : geography types, distance, point-in-zone
--   btree_gist : lets the no-double-booking exclusion constraint combine
--                "same Talent" (=) with "overlapping time" (&&) in one index
\connect gigwork_core
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS btree_gist;

\connect gigwork_worker
CREATE EXTENSION IF NOT EXISTS postgis;
