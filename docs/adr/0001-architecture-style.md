# ADR-0001: Hybrid architecture — modular-monolith core with separate fan-out worker and payments services

- Status: Proposed
- Date: 2026-11-10
- Related: PRD (Non-functional requirements, Matching and notifications, Payments and escrow), ADR-0002 (data ownership), ADR-0003 (module boundaries), ADR-0004 (booking–payment consistency)

## Context

GigWork is built by one developer working 15–20 hours a week. Phase 1 must ship by 6 Dec 2026, and running costs should stay around $30/month on AWS outside load-test days.

The PRD names six product areas: identity, profiles, gigs (including applications and bookings), matching, notifications and payments.

The forces that shape this decision:

- **Two different load shapes.** Most of the product is steady request/response traffic: profile edits, gig posts, search, applications. Publishing a gig is different. It triggers a burst: one event becomes a matching query over tens of thousands of profiles and then thousands of notifications. That work is CPU- and IO-heavy and must not slow the API down.
- **Strong invariants inside the booking flow.** Selecting an applicant must update the application, fill the gig, create a busy block and write an outbox event atomically, so that a Talent can never be double-booked.
- **A planned Java vs Go benchmark.** The fan-out path is to be implemented in both Java 21 and Go and load-tested under identical conditions. That requires the fan-out logic to be a separately deployable unit, so either implementation can be placed behind the same Kafka topics.
- **Money needs isolation.** Payments (Phase 4, gateway in test mode) involves a ledger, gateway webhooks and secrets. Its correctness and security requirements are stricter than the rest of the system's, and it changes less often.
- **Learning goals.** GigWork is also a portfolio project. A deliberate goal is hands-on experience with distributed transactions (sagas).

## Options considered

### A. Microservices from day one

One service per product area, each with its own database.

- Pro: Each area can scale and deploy independently.
- Con: Selecting an applicant spans gigs, bookings and profiles, so it becomes a distributed transaction across three services.
- Con: Six deployables, network failures between our own components, and higher cost. Phase 1 would very likely slip.
- Con: For a solo project with no users, this is hard to justify.

### B. Layered monolith

One deployable organised by technical layer (controllers → services → repositories), with all tables shared.

- Pro: Fastest to start and simplest to run.
- Con: Nothing prevents any code from reading or writing any table. Coupling grows silently, and no part can be extracted later without a rewrite.
- Con: Fan-out bursts run in the same process as the API.

### C. Modular monolith

One deployable and one Postgres. The code is split into modules that each own their tables and talk only through public interfaces or events.

- Pro: Nearly as fast to build as B, and just as cheap to run.
- Pro: Cross-module invariants (selection + busy block + outbox) stay in one local transaction.
- Pro: Kafka-based fan-out is still possible, and modules can later be extracted along boundaries that already exist.
- Con: One failure domain and one scaling unit: a fan-out burst competes with API traffic for CPU, memory and database connections.
- Con: The Go benchmark would need the fan-out logic pulled out anyway.

### D. Hybrid: modular-monolith core plus two separate services (chosen)

- **Core service** (modular monolith): identity, profiles (including availability), gigs (including applications and bookings).
- **Fan-out worker**: matching + notifications. Consumes `GigPublished` and produces `NotifyTalent`.
- **Payments service**: escrow, ledger, gateway integration. Linked to booking through a saga.
- Pro: The burst workload and the money workload are isolated at runtime, while the core keeps local transactions for its invariants.
- Pro: The fan-out worker can be scaled, or reimplemented in Go, independently.
- Con: Three deployables instead of one, and the operations and contract-versioning cost that comes with them, paid before load tests prove they're needed.
- Con: Booking ↔ payment consistency requires a saga instead of a local transaction.

## Decision

We will build **Option D**: a modular-monolith core plus two separate services.

**Fan-out worker (matching + notifications) is a separate service** because its load is bursty while the API's load is steady. A single gig publish can trigger matching over tens of thousands of profiles and thousands of notifications, and running that inside the core would make that burst compete with API requests for CPU and database resources. Separating it also makes the planned Java vs Go comparison possible, since both implementations consume the same topics and produce the same events. We accept the extra deployable and the need for versioned event contracts.

**Payments is a separate service** to isolate money movement, the ledger, gateway secrets and webhook handling from the faster-changing rest of the system. It is also a deliberate learning choice: it makes booking ↔ payment consistency a real saga problem. We accept that this is more work than a payments module inside the core would be, and that it lands in Phase 4.

**Everything else stays in the core as a modular monolith**, so that invariants within the core (especially "a Talent is never double-booked") stay in a single local database transaction.

## Consequences

**What gets easier**

- Fan-out bursts cannot use up the core API's compute. The worker scales on its own.
- The fan-out path can be swapped between Java and Go for benchmarking without touching the core.
- Within the core, selection + busy block + outbox commit atomically, with no distributed transaction.
- Payments code is small and isolated, which makes the ledger and webhook handling easier to reason about and test.

**What gets harder**

- Three deployables to build, run in Docker Compose, deploy and monitor (logs, traces, consumer lag).
- Events between services become **contracts**: `GigPublished`, `NotifyTalent` and the profile/availability events consumed by the worker need schemas and a versioning rule.
- Booking confirmation becomes a saga with compensating steps. The booking state machine needs a "payment pending" state from the start, even though payment code arrives in Phase 4.
- Phase 4 scope grows. This is recorded as a risk against the job-hunt timeline.

**What we now have to build or decide**

- A transactional outbox and relay in the core (Phase 1–2).
- How the worker gets matching data → **ADR-0002**.
- How module boundaries inside the core are enforced → **ADR-0003**.
- Saga style for booking ↔ payment (choreography vs orchestration) → **ADR-0004**, written before the gig/booking state machine ADR.

**What would make us revisit this decision**

- **Fan-out worker:** if Phase 3 load tests show the core API keeps its p99 target while handling a 3,000-recipient fan-out in-process, *and* the Java vs Go comparison is finished, consider folding the worker back into the core as a module.
- **Payments:** if the saga work makes Phase 4 slip by more than two weeks, fold payments into the core as a module (keeping its own tables and gateway adapter) and supersede this ADR. If GigWork moves toward a real launch, revisit for compliance reasons. That would likely strengthen the separation rather than reverse it.
- **Core:** if one core module shows a load profile or change pace clearly different from the rest (measured, not assumed), consider extracting it, with its own ADR.
