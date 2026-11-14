# Architecture Decision Records

Each ADR records one significant decision: the context, the options considered, what was chosen, and the consequences. New ADRs copy `0000-template.md`. An accepted ADR is never edited to change its decision; it is superseded by a new ADR.

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-architecture-style.md) | Modular-monolith core with a separate matching and notifications worker | Accepted |
| [0002](0002-datastores.md) | PostgreSQL with PostGIS as the system of record, partitioned Postgres for the notification inbox, Redis for rate limits | Accepted |
| [0003](0003-data-ownership.md) | A database per service, a schema per core module, compacted topics for the worker's copy | Accepted |
| [0004](0004-module-boundaries.md) | Module boundaries enforced with Spring Modulith and ArchUnit; custom outbox for events leaving the core | Accepted |
| [0005](0005-authentication.md) | OTP login built in Identity, JWT access tokens with rotating refresh tokens | Accepted |
| [0006](0006-feature-flags.md) | Feature flags through OpenFeature, backed by our own PostgreSQL provider | Accepted |
| [0007](0007-inbox-api-and-routing.md) | The worker serves the inbox API behind a single path-based router | Accepted |
| [0008](0008-persistence-convention.md) | JPA by default; single native SQL statements for writes that enforce invariants | Accepted |
| [0009](0009-endpoint-security.md) | Security configured in the Platform module; each module declares its own public endpoints | Accepted |
| [0010](0010-scheduled-jobs.md) | Scheduled jobs are idempotent SQL sweeps, run by one instance at a time through ShedLock | Accepted |
Related design documents: [PRD](../prd.md) · [Components and flows](../design/components-and-flows.md) · [Data model](../design/data-model.md)
