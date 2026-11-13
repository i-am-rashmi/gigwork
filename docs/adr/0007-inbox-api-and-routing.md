# ADR-0007: The worker serves the inbox API behind a single path-based router

- Status: Proposed
- Date: 2026-11-13
- Related: ADR-0001, ADR-0002, ADR-0003, ADR-0005, NFR-7, NFR-32, NFR-40

## Context

The in-app inbox is stored in the Matching & Notifications worker's database (ADR-0002, ADR-0003). Users read it through a small set of endpoints, called on every app open:

- list my latest notifications, newest first
- get my unread count
- mark one notification as read
- mark all as read

Everything else users do goes to the core. The question is which deployable answers the inbox endpoints, and how users reach both services.

The worker has two different jobs: fan-out (consuming events and writing inbox items in bursts of up to 30,000) and serving inbox reads (steady and latency-sensitive). Only fan-out is part of the planned Java vs Go benchmark.

Rolling deploys (NFR-32) already require at least two core instances behind a load balancer.

## Options considered

1. **The worker serves the inbox API**
   - Pro: the data's owner serves its data, with no extra hops; tokens are verified locally with the public key (ADR-0005).
   - Con: the worker becomes user-facing; a fan-out burst in the same process could slow inbox reads; the Go implementation would have to include the API.
2. **The core forwards inbox requests to the worker**
   - Pro: one public entry point.
   - Con: an extra network hop, and inbox requests fail when either service is down.
3. **The core stores the inbox**
   - Con: the 30,000-item bursts would land on the core's database, undoing ADR-0002 and ADR-0003. Rejected.
4. **A router in front of both services**, sending inbox paths to the worker and everything else to the core
   - Pro: one public address; no hop through the core; the internal layout is hidden, so services can be split or moved without clients noticing.
   - Pro: it is the load balancer already required for rolling deploys, plus one routing rule.
   - Con: adds roughly 1–2 ms per request in the same data centre, negligible against the 200 ms read target (NFR-7).

## Decision

- **A single path-based router is the only public entry point:** /me/notifications/\*\* goes to the worker's inbox API; everything else goes to the core instances. In the cloud this is an Application Load Balancer; locally, a small Nginx or Traefik container.
- **The router only routes.** HTTPS terminates there, but authentication and rate limits stay in each service (ADR-0005, NFR-40), so every service remains secure on its own.
- **The worker serves the inbox API itself**, as a separate process from the fan-out consumer, built from the same codebase:
  - fanout-consumer: consumes events and writes inbox items (Java, and Go for the benchmark).
  - inbox-api: serves inbox reads and read-marking (Java only).
  - Both use the worker's database through the same InboxStore contract.

## Consequences

**What gets easier**

- Users and future clients see one API address.
- Fan-out bursts can't slow inbox reads, since they run in separate processes.
- The Java vs Go comparison covers only the fan-out consumer; the inbox API is written once.
- Extracting a core module later only needs a new routing rule.

**What gets harder**

- Three processes to run (core, fanout-consumer, inbox-api) plus the router, locally and in each environment.
- The inbox API needs the same operational care as the core: health checks, rate limits, monitoring.
- The Go fan-out consumer must write inbox items exactly as the Java InboxStore contract defines, verified by contract tests (NFR-65).

**What would make us revisit this decision**

- Needing edge features such as token checks, request transformation or per-client quotas → consider a full API gateway product.
- The inbox moving to DynamoDB (ADR-0002) → the inbox-api's storage changes behind InboxStore; routing stays the same.
