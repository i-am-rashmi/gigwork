# Core service

Spring Boot modular monolith. Root package: `com.gigwork.core`.

## Modules (ADR-0004)

Each top-level package under `com.gigwork.core` is a Spring Modulith module:
`identity`, `profile`, `availability`, `reference`, `gigs`, `aidrafting`, `hiring`, `discovery`, `platform`, `moderation`.

- A module's **base package is its public API**: services, DTOs (Java records) and events other modules may use.
- **Sub-packages are internal** (e.g. `domain`, `persistence`, `web`). Other modules must never use them.
- Each module owns the database schema of the same name (ADR-0003). `aidrafting` uses the `ai_drafting` schema.

## Rules across modules

- Refer to another module's data **by ID only**. No JPA relationships (`@ManyToOne` etc.) across modules.
- Module APIs take and return their own **records/DTOs**, never internal entities.
- Need an answer now (or same transaction) → call the other module's public service.
- Can happen a moment later → publish an event with `ApplicationEventPublisher`; listen with `@ApplicationModuleListener`. Listeners must be idempotent.
- Events that leave the core (to Kafka) go **only** through the platform module's outbox, never straight to Kafka.
- Never query another module's tables, including in native SQL.

## Database and migrations

- Flyway owns the schema: `src/main/resources/db/migration/V{n}__{description}.sql`. Hibernate only validates (`ddl-auto: validate`).
- **Never edit a migration that is already on `main`.** Add a new one.
- Migrations must be backward compatible (NFR-70): add first, switch code, remove later. No renames or drops of columns still used by the running version.
- Always schema-qualify tables: `availability.booking`, not `booking`.
- Invariants belong in the database where possible (ADR-0002): unique constraints, partial unique indexes, the exclusion constraint for overlapping bookings, conditional updates such as `UPDATE ... WHERE status = 'OPEN'`. Don't replace these with check-then-act code.
- IDs are random UUIDs, never sequences (NFR-33). City-local rows carry `city_id`.

## Code style

- Java records for DTOs, events and value objects. No Lombok.
- Constructor injection only; no field injection.
- Validate input on the server with Bean Validation (`@NotNull`, `@Size`, ...). Never trust the client.
- Never log mobile numbers, emails, OTP codes or tokens (NFR-46).
- Times are `Instant`/`OffsetDateTime` in UTC; stored as `TIMESTAMPTZ`.

## Tests

- **Unit tests** for domain logic.
- **Module tests** with `@ApplicationModuleTest` for each module.
- **Integration tests** against real PostgreSQL + PostGIS via Testcontainers (`TestcontainersConfiguration`). Don't use H2: constraints and PostGIS can't be tested on it.
- **Concurrency tests** for every invariant that must hold under simultaneous requests (double-booking, one application per gig, one pending hire per gig).
- The modularity test (`ApplicationModules.of(...).verify()`) and ArchUnit rules must keep passing.
