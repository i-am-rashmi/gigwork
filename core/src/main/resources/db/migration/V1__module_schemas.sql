-- One schema per core module (ADR-0003). Tables arrive in later migrations.

-- Extensions are created by the local init script; IF NOT EXISTS keeps this
-- migration working in Testcontainers, where the test user is a superuser.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS profile;
CREATE SCHEMA IF NOT EXISTS availability;
CREATE SCHEMA IF NOT EXISTS reference;
CREATE SCHEMA IF NOT EXISTS gigs;
CREATE SCHEMA IF NOT EXISTS ai_drafting;
CREATE SCHEMA IF NOT EXISTS hiring;
CREATE SCHEMA IF NOT EXISTS discovery;
CREATE SCHEMA IF NOT EXISTS platform;
CREATE SCHEMA IF NOT EXISTS moderation;
