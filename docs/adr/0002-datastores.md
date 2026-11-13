# ADR-0002: PostgreSQL with PostGIS as the system of record, partitioned Postgres for the notification inbox, Redis for rate limits

- Status: Proposed
- Date: 2026-11-13
- Related: GigWork — Requirements (NFR-1–16, NFR-23, NFR-26, NFR-37–40, NFR-47, NFR-49), GigWork — Data Model ("Data shapes across GigWork"), ADR-0001 (architecture style)

## Context

The data model shows four distinct data shapes, each with different needs:

1. **Relational and transactional** (Identity, Talent Profile, Availability, Gigs, Hiring, Platform, Moderation, AI Drafting sessions): transactions across several entities, uniqueness, no-overlap rules, conditional status changes, joins. Tens of millions of rows a year at design scale, mostly applications.
2. **Geographic** (zone lookups and distance in Talent Profile, Gigs and Reference Data; radius matching in the fan-out worker's copy): point-in-zone, nearest zone, within-radius queries combined with ordinary filters.
3. **Huge, append-only, expiring** (the notification inbox): about 3 million items a day per city at peak, burst writes of up to 30,000 in 60 seconds, reads only by recipient, deletion after 90 days; about 270 million rows per city at design scale.
4. **Tiny, hot, short-lived** (rate-limit counters; caches of reference data and feature flags): updated or read on almost every request, worthless after minutes.

The most important invariants are concurrency rules that must hold under simultaneous requests: no double-booking of a Talent (NFR-14), one application per Talent per gig (NFR-16), at most one pending hire per gig, and a gig filled only once.

The guiding principle: start from the invariants, then choose the store whose atomicity boundary covers them. Prefer rules the database enforces declaratively over check-then-act logic in application code, which is vulnerable to race conditions (write skew) under load.

## Options considered

### Shape 1: relational and transactional

1. **PostgreSQL**
   - Pro: expresses every invariant declaratively: exclusion constraints (no overlapping bookings per Talent), partial unique indexes (one pending hire per gig), conditional updates, multi-entity ACID transactions, and FOR UPDATE SKIP LOCKED for the outbox and scheduled actions.
   - Pro: open source, runs anywhere, widely known; PostGIS available for shape 2.
   - Con: scaling beyond one machine (sharding) is harder than in key-value stores; far beyond our design scale.
2. **MySQL**
   - Pro: reliable, transactional, widely known.
   - Con: no exclusion constraints or partial unique indexes; the no-overlap and one-pending-hire rules need hand-written locking or workarounds. Weaker geographic support.
3. **MongoDB**
   - Pro: flexible documents.
   - Con: atomic only within one document natively; the no-overlap rule needs a per-Talent calendar document, and the related gig, hire and outbox writes still need multi-document transactions. Our data is highly connected, so joins are awkward.
4. **DynamoDB**
   - Pro: near-unlimited scale, managed, conditional writes.
   - Con: no range constraints, joins or ad-hoc queries; every access pattern must be designed up front; double-booking needs time-slot items; AWS-only.

### Shape 2: geographic

1. **PostGIS inside PostgreSQL**
   - Pro: geography types, distance functions and spatial indexes; geographic filters combine with ordinary filters in one query and one transaction; no extra system.
2. **A separate geographic or search store** (Elasticsearch/OpenSearch, Redis geo commands)
   - Con: another system kept in sync by events, and it can't join with the rest of the data.
3. **Distance computed in application code**
   - Con: no index, so every query scans everything.

### Shape 3: the notification inbox

1. **PostgreSQL, single table**
   - Con: deleting about 3 million rows a day creates dead tuples, constant vacuum work and table and index bloat.
2. **PostgreSQL with time partitioning** (one partition per day or week)
   - Pro: expiry by dropping whole partitions: instant, with no vacuum work.
   - Pro: same technology already in use; simple local development and demos.
   - Con: a unique constraint on a partitioned table must include the partition key, so duplicate prevention needs a separate dedupe table. Partitions must be created ahead of time.
3. **DynamoDB** (partition key = recipient, sort key = time)
   - Pro: the access pattern is exactly what it is built for; native TTL deletes expired items; writes spread naturally across recipients.
   - Con: dedupe and the unread count must be maintained explicitly (two writes per notification); another technology to run; AWS-only, needing an emulator locally; cost grows per request.
4. **Cassandra or ScyllaDB**
   - Pro: the same data model as DynamoDB with native per-row TTL and excellent write throughput.
   - Con: needs a cluster of at least three nodes to run properly; too much operational weight for one table.

### Shape 4: rate limits and caches

1. **Redis (or Valkey)**
   - Pro: atomic increments with automatic expiry, shared by every app instance.
2. **In-memory inside each app instance**
   - Pro: fastest, no network hop.
   - Con: counters are per instance, so limits aren't enforced correctly with two or more instances.
3. **PostgreSQL**
   - Con: a database write on almost every request for data discarded minutes later.

## Decision

- **Shape 1: PostgreSQL** is the system of record for the core service. Invariants are enforced by the database: an exclusion constraint for no overlapping active bookings per Talent, a unique constraint for one application per Talent per gig, a partial unique index for one pending hire per gig, and conditional updates for gig status changes.
- **Shape 2: PostGIS** inside the same PostgreSQL, for zone lookups, nearest zone, distance and radius matching (including the fan-out worker's copy of Talent data).
- **Shape 3: time-partitioned PostgreSQL** for the notification inbox, in the fan-out worker's own schema, with a separate dedupe table holding the unique key (gig, recipient, type). Expiry is done by dropping partitions older than 90 days. The inbox sits behind its own interface in the worker's code, so the store can be replaced without changing the matching logic. **DynamoDB is the planned store for a real launch**, or earlier if load tests require it.
- **Shape 4: Redis** for rate-limit counters, shared by all instances. Reference data and feature flags are cached in memory inside each app instance and refreshed within their staleness limits (NFR-21, NFR-49).

## Consequences

**What gets easier**

- The most dangerous bugs (double-bookings, duplicate applications, two pending hires, a gig filled twice) are prevented by the database rather than by code that every developer, including the AI agent, must get right.
- One relational database covers most of the system: one technology to provision, back up, monitor and run locally.
- Geographic queries combine with ordinary filters in one query.
- Inbox expiry costs almost nothing.

**What gets harder**

- Three data technologies to run: PostgreSQL (with PostGIS), Redis and Kafka.
- Partitions for the inbox must be created ahead of time and old ones dropped by a scheduled job.
- Duplicate prevention for the inbox lives in a separate dedupe table that must be cleaned up.
- Scaling PostgreSQL beyond one machine is harder than with a key-value store, if it is ever needed.

**What we now have to build or decide**

- Exclusion constraint and partial unique indexes in the first migrations (requires the btree\_gist extension alongside PostGIS).
- Partition management for the inbox (pg\_partman or a scheduled job) and dedupe-table cleanup.
- The InboxStore interface in the worker.
- How each service's data is separated within the PostgreSQL instance → data-ownership ADR.

**What would make us revisit this decision**

- **Inbox:** if the Phase 2 load test can't meet NFR-11 or NFR-12 with partitioned PostgreSQL, or when GigWork prepares for a real launch, move the inbox to DynamoDB.
- **Search:** if PostgreSQL can't meet NFR-9 (search p95 < 300 ms at 50,000 profiles) in Phase 3, add a search index fed by events.
- **Core:** if the core database approaches the limits of one instance (sustained high CPU or storage growth beyond plan), add read replicas first, then consider partitioning or sharding by city.
