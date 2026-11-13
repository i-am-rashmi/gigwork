# ADR-0003: A database per service, a schema per core module, and compacted topics for the worker's copy

- Status: Proposed
- Date: 2026-11-13
- Related: ADR-0001 (architecture style), ADR-0002 (datastores), GigWork — Components and Flows, GigWork — Data Model

## Context

ADR-0001 splits GigWork into a modular-monolith core and a separate Matching & Notifications worker; ADR-0002 puts both on PostgreSQL (with PostGIS). Three questions remain about who owns which data and how that is enforced:

1. How the core's data is separated from the worker's.
2. How each module's data is separated inside the core, given that some flows must commit across modules in one transaction (confirming a hire changes Gigs, Availability and Hiring together, NFR-14).
3. How the worker rebuilds its copy of Talent data (NFR-18) if the copy is lost, or when a new worker starts (for example, the Go implementation for the benchmark).

The worker's data differs sharply from the core's: a time-partitioned inbox of about 270 million rows per city, a version-checked copy of Talent data, and a planned move of the inbox to DynamoDB for a real launch.

## Options considered

### 1. Separating the core and the worker

1. **One database, a schema per service, a database user per service**
   - Pro: simplest; one database to back up.
   - Con: cross-service queries are possible and only blocked by permissions; one wrong grant opens a gap.
2. **One PostgreSQL instance, a database per service**
   - Pro: cross-service queries are impossible, since PostgreSQL can't join across databases.
   - Pro: moving the worker to its own server later is a connection-string change.
   - Con: two databases to create and migrate; PostGIS enabled in both.
3. **A separate instance per service**
   - Pro: full physical isolation; fan-out bursts can't affect the core's database.
   - Con: double the database cost and setup, before any measurement shows the need.

### 2. Separating modules inside the core

1. **One shared schema, ownership by convention**
   - Con: ownership is invisible and nothing groups a module's tables for later extraction.
2. **A schema per module, one database user for the core**
   - Pro: ownership is visible (availability.booking belongs to Availability); cross-module transactions still work; a module's tables are already grouped if it is ever extracted.
3. **A database user per module**
   - Con: separate connections mean separate transactions, which breaks the hire transaction (flow 7).

### 3. Rebuilding the worker's copy

1. **Replay from compacted Kafka topics**
   - Pro: log compaction keeps at least the latest event per key (Talent ID) indefinitely, so a new worker reading from the beginning ends with every Talent's current state, with no load on the core.
   - Con: depends on the Kafka data being intact.
2. **A backfill job in the core** that republishes current profiles and availability as events on demand
   - Pro: always works, even if Kafka data is lost.
   - Con: extra code and load on the core.

## Decision

- **A database per service on one PostgreSQL instance:** gigwork\_core and gigwork\_worker, each accessed by its own database user. A separate instance for the worker is the next step if load tests show its bursts affecting the core.
- **A schema per module inside the core database** (identity, profile, availability, reference, gigs, ai\_drafting, hiring, discovery, platform, moderation), with **one database user for the core** so cross-module transactions keep working. No module queries another module's tables directly; it goes through that module's code. This is enforced by build-time checks (module-boundaries ADR), not by database permissions.
- **The worker's copy is rebuilt from compacted Kafka topics** (talent-events and availability-events, keyed by Talent ID), with a "republish all" backfill job in the core as the emergency fallback.
- **Conventions that keep later scaling open:** random UUIDs rather than database sequences (NFR-33), and a city ID on city-local rows (gigs, profiles, bookings).

## Consequences

**What gets easier**

- The worker can't depend on the core's tables by accident, and can move to its own server or to DynamoDB without touching the core.
- Each module's data is visibly owned and grouped, which keeps extraction possible.
- A new worker (including the Go implementation) builds the same copy by reading the topics from the start.

**What gets harder**

- Two databases to create, migrate and back up.
- Compacted topics need deliberate configuration (cleanup policy, retention), and events must always carry the full current state for their key, not just the changed fields.
- The rule against cross-module SQL needs automated enforcement to survive day-to-day work, including AI-written code.

**Scaling path** (each step only when measurement demands it): a bigger instance → read replicas for heavy reads → partitioning large tables → extracting a module to its own database → sharding by city. Gigs, Availability and Hiring share the hire transaction, so they are extracted only together, as one transactional core; Identity, Talent Profile, Reference Data, AI Drafting and Moderation can be extracted independently. Remote gigs and accounts are not city-bound and would stay in shared stores if sharding by city is ever needed.

**What would make us revisit this decision**

- Worker bursts measurably slowing core queries → move the worker database to its own instance.
- A module whose load or growth clearly differs from the rest → extract it, with its own ADR.
