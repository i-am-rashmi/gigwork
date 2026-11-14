# Core service

Spring Boot modular monolith. Root package: `com.gigwork.core`.

## Modules (ADR-0004)

Each top-level package under `com.gigwork.core` is a Spring Modulith module:
`identity`, `profile`, `availability`, `reference`, `gigs`, `aidrafting`, `hiring`, `discovery`, `platform`, `moderation`.

- A module's **base package is its public API**: the types, DTOs (Java records) and events other modules may use, and nothing else.
- **Sub-packages are internal** (see Module layout). Other modules must never use them.
- Each module owns the database schema of the same name (ADR-0003). `aidrafting` uses the `ai_drafting` schema.

## Module layout

Inside each module (all sub-packages are internal):

- `web`: controllers and exception handlers
- `web/dto`: request and response records of the HTTP API
- `application`: use cases (services, transactions)
- `domain`: types, rules and exceptions
- `persistence`: JPA entities and repositories
- `config`: configuration classes and properties

A module may have additional sub-packages for a distinct technical concern that would crowd a layer (for example identity's `security` and `otp` packages). Name them after the concern, not a pattern.

The base package holds only the module's public API. Result records that never leave the module stay next to the code that returns them.

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
- **JPA by default (ADR-0008).** Entities and Spring Data repositories for ordinary reads and writes. Any write that enforces an invariant (conditional status change, counter with a limit, token rotation, `ON CONFLICT` insert) is **one native SQL statement** with a concurrency test, never load-modify-save.
- **No network calls (HTTP, SMS, email, Kafka) inside a database transaction.**
- Invariants belong in the database where possible (ADR-0002): unique constraints, partial unique indexes, the exclusion constraint for overlapping bookings, conditional updates such as `UPDATE ... WHERE status = 'OPEN'`. Don't replace these with check-then-act code.
- IDs are random UUIDs, never sequences (NFR-33). City-local rows carry `city_id`.

## Code style

- Java records for DTOs, events and value objects. No Lombok.
- Constructor injection only; no field injection.
- Never throw generic exceptions (`Exception`, `RuntimeException`, `Throwable`). Throw specific, named exceptions instead, e.g. `GigNotOpenException` or `TalentAlreadyBookedException`. ArchUnit's `NO_GENERIC_EXCEPTIONS` rule enforces this and fails the build.
- Validate input on the server with Bean Validation (`@NotNull`, `@Size`, ...). Never trust the client.
- Never log mobile numbers, emails, OTP codes or tokens (NFR-46).
- Times are `Instant`/`OffsetDateTime` in UTC; stored as `TIMESTAMPTZ`.
- Timestamps are truncated to microseconds (PostgreSQL's precision, which rounds anything finer) before they are stored or compared. Use the injected `Clock`, which does this, never `Instant.now()`. On Linux `Instant.now()` has nanoseconds, so a value compared with its stored copy can differ by up to half a microsecond.

## Seed data (reference)

- Reference data (categories, specialisations, languages, cities, zones, localities) is seeded from `infra/seed/` by **repeatable** Java migrations listed in `src/main/resources/db/seed-migrations.csv`; see `infra/seed/README.md`. Editing a seed file is how reference data changes until Admin editing exists (Phase 3); Flyway reloads the migrations whose files changed on the next start. A reload upserts by stable key and retires (never deletes) rows that left the files. They are repeatable, not versioned, so they never collide with SQL migration versions.
- Test the upgrade path, not just fresh databases: CI migrates an empty database, which cannot show what an edited seed file does to an existing one (see `SeedMigrationFlywayTest`).
- **The build reads `../infra/seed`** (Maven copies it into the jar), so the build needs the repository root. Any future Docker build must use the repo root as its context, not `core/`.

## Authentication and configuration (identity)

- Every endpoint needs a bearer access token except the ones declared public (currently `POST /v1/auth/otp/request`, `/v1/auth/otp/verify`, `/v1/auth/token/refresh`, the health probes and the API docs). **Public endpoints are declared in the owning module with a `PublicEndpoints` bean (ADR-0009), never by editing the security configuration** (which lives in the platform module). Public means "no token required": an invalid or expired token is still a 401. `PublicEndpointsTest` lists every public endpoint, so adding one means changing that test on purpose.
- All endpoints live under `/v1`. Controllers declare paths **without** it: the platform module adds the prefix (`ApiPaths.V1`) in one place.
- Look accounts up only by the keyed hash of the normalised mobile number (`KeyedHasher`, `MobileNumber`), never by the number itself. Never log mobile numbers, OTP codes or tokens; give request/response records a masking `toString`.
- Secrets have no defaults. Locally use the `local` profile (fake keys prefixed `dev-only-not-a-secret-`, dev OTP sender, generated JWT keys, Swagger UI). `DevConfigurationGuard` refuses to start with any of these unless every active profile is `local` or `test`.
- Which `OtpSender` exists is chosen by `gigwork.identity.otp.sender` (`none` by default, `dev` locally): deployment configuration, not a runtime flag.

## Tests

- **Unit tests** for domain logic.
- **Module tests** with `@ApplicationModuleTest` for each module.
- **Integration tests** against real PostgreSQL + PostGIS via Testcontainers (`TestcontainersConfiguration`). Don't use H2: constraints and PostGIS can't be tested on it.
- **Concurrency tests** for every invariant that must hold under simultaneous requests (double-booking, one application per gig, one pending hire per gig).
- The modularity test (`ApplicationModules.of(...).verify()`) and ArchUnit rules must keep passing.
