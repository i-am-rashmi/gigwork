# ADR-0001: Modular-monolith core with a separate matching and notifications worker

- Status: Proposed
- Date: 2026-11-10, revised 2026-11-13
- Related: GigWork — Requirements, GigWork — Components and Flows, GigWork — Data Model, ADR-0002 (datastores)

## Context

GigWork is built by one developer working 4–5 hours a day, with Phase 1 expected around late December to mid-January. Environments are ephemeral (created for demos and load tests, then destroyed), so the running cost when idle is close to zero.

The design has 11 components: Identity & Access, Talent Profile, Availability, Reference Data, Gigs, AI Drafting, Hiring, Matching & Notifications, Discovery, Platform and Moderation.

The forces that shape this decision:

- **Two different load shapes.** Most of the product is steady request/response traffic (about 100 requests a second per city at design scale). Publishing a gig is different: one event becomes a matching query over tens of thousands of profiles and up to 30,000 notifications in under a minute (NFR-11, NFR-12). That burst must not slow the API down.
- **Strong invariants inside the core.** Confirming a hire must fill the gig, create a booking (no double-booking, NFR-14), update the hire and write outbox events in one transaction.
- **A planned Java vs Go benchmark.** The fan-out path is to be implemented in both Java and Go and load-tested under identical conditions, so it must be a separately deployable unit behind the same Kafka topics.
- **The notification inbox is the largest dataset by far** (about 270 million rows per city at design scale) with a very different access pattern from the core (ADR-0002).
- **Payments are not in any phase.** Gig payments stay offline with no commission; platform purchases (subscriptions, boosts) are parked under monetisation readiness.

## Options considered

### A. Microservices from day one

One service per component, each with its own database.

- Pro: each area can scale and deploy independently.
- Con: confirming a hire spans Gigs, Availability and Hiring, so it would become a distributed transaction (a saga) for no product benefit.
- Con: about ten deployables, network failures between our own components, and much more to run. Hard to justify for a solo project with no users.

### B. Layered monolith

One deployable organised by technical layer (controllers, services, repositories), with all tables shared.

- Pro: fastest to start and simplest to run.
- Con: nothing prevents any code from reading or writing any table, so coupling grows silently and no part can be extracted later.
- Con: fan-out bursts run in the same process as the API.

### C. Modular monolith only

One deployable and one database, split into modules that own their data and talk only through public interfaces or events.

- Pro: nearly as fast to build as B and just as cheap to run; cross-module invariants stay in one local transaction.
- Con: fan-out bursts compete with API traffic for CPU, memory and database connections.
- Con: the Java vs Go benchmark would need the fan-out logic pulled out anyway.

### D. Modular-monolith core plus a separate matching and notifications worker (chosen)

- **Core service** (modular monolith): Identity & Access, Talent Profile, Availability, Reference Data, Gigs, AI Drafting, Hiring, Discovery, Platform, Moderation.
- **Matching & Notifications worker**: its own copy of Talent data, matching and fan-out, the in-app inbox and delivery. It consumes events from Kafka and never reads the core's tables.
- Pro: the burst workload is isolated at runtime while the core keeps local transactions for its invariants.
- Pro: the worker can be scaled, or reimplemented in Go, independently.
- Con: two deployables instead of one, and event contracts that must be versioned.

## Decision

We will build **Option D**: a modular-monolith core plus one separate service, the Matching & Notifications worker.

**The worker is separate** because its load is bursty while the API's load is steady, because its data (the inbox) is far larger and shaped differently from the core's, and because the planned Java vs Go comparison needs a swappable unit behind the same topics. We accept a second deployable and versioned event contracts.

**Everything else stays in the core as a modular monolith**, so invariants that span modules (most importantly that a Talent is never double-booked) stay in a single local database transaction.

**Payments are deferred.** No payments service is built now. When monetisation is decided (subscriptions and gig boosts), a separate ADR will decide where payments live; boost purchases are the natural place to practise a saga.

## Consequences

**What gets easier**

- Fan-out bursts can't use up the core API's compute; the worker scales on its own.
- The fan-out path can be swapped between Java and Go for benchmarking without touching the core.
- Within the core, confirming a hire commits atomically, with no distributed transaction.
- The design describes only what is being built.

**What gets harder**

- Two deployables to build, run locally, deploy and monitor (logs, traces, consumer lag).
- Events between the core and the worker are contracts (GigPublished, TalentProfileChanged, AvailabilityChanged, hiring events) and need schemas and a versioning rule.
- Module boundaries inside the core must be enforced deliberately, or the monolith decays into Option B.

**What we now have to build or decide**

- A transactional outbox and relay in the core (Phase 1).
- How module boundaries in the core are enforced → module-boundaries ADR.
- How each service's data is separated in PostgreSQL → data-ownership ADR.
- Whether the worker or the core serves the inbox API → its own ADR.
- Where payments live, once monetisation is decided → a future ADR.

**What would make us revisit this decision**

- **Worker:** if Phase 2 load tests show the core keeps its p95 targets while handling a 9,000-recipient fan-out in-process, and the Java vs Go comparison is finished, consider folding the worker back into the core as a module.
- **Core:** if one module shows a load profile or change pace clearly different from the rest (measured, not assumed), consider extracting it, with its own ADR.
- **Payments:** revisit when monetisation moves from parked to planned.
