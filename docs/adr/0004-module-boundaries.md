# ADR-0004: Module boundaries enforced with Spring Modulith and ArchUnit

- Status: Proposed
- Date: 2026-11-13
- Related: ADR-0001 (architecture style), ADR-0003 (data ownership), NFR-65, NFR-67, NFR-69

## Context

The core service is a modular monolith with ten modules (ADR-0001), each owning its own schema (ADR-0003). In a monolith any class can technically call any other, so boundaries erode one shortcut at a time: a module reads another module's repository, entities link across modules, cycles appear. Once entangled, modules can no longer be understood, tested or extracted on their own.

Much of the code is written by an AI coding agent and reviewed by a human (NFR-69). Review alone won't reliably catch boundary violations, so the rules must be enforced automatically, failing the build like any other test.

The core is a Spring Boot application, and a realistic path to extracting modules into separate services later should stay open.

## Options considered

1. **Java visibility (package-private) and convention**
   - Pro: no tooling.
   - Con: Java packages are flat: a sub-package is unrelated to its parent for access purposes, so module internals spread over sub-packages must be public and become visible to every module. Protected members are reachable by any subclass in any package. In practice this decays.
2. **ArchUnit**
   - Pro: architecture rules written as JUnit tests over the compiled bytecode; explicit and flexible (module internals, layering, cycles, annotations, custom rules).
   - Con: every rule is written and maintained by hand; can't see raw SQL strings or runtime reflection.
3. **Spring Modulith**
   - Pro: derives module rules from a package convention (each top-level package is a module; its base package is the public API, sub-packages are internal) and verifies them in one test, including cycles.
   - Pro: module integration tests that boot a single module, an event publication registry with optional externalisation to Kafka, and module documentation generated from the code.
   - Con: tied to Spring and its package conventions.
4. **Maven or Gradle multi-module build**
   - Pro: the compiler enforces boundaries.
   - Con: ten or more build modules (often twenty with API and implementation split), slower builds and much more configuration for a solo project.
5. **Java Platform Module System**
   - Con: friction with Spring's use of reflection; rarely used in Spring Boot applications.

## Decision

We will use **Spring Modulith** for module boundaries and **ArchUnit** for rules beyond them.

**Spring Modulith**

- Each top-level package under the application root is a module: identity, profile, availability, reference, gigs, aidrafting, hiring, discovery, platform, moderation.
- A module's base package is its public API; sub-packages are internal.
- One test verifies the structure, including the absence of cycles, on every build.
- Each module has a module integration test that boots it on its own.

**ArchUnit rules**

- Controllers don't use repositories directly; layering inside each module is respected.
- Only the platform module writes outbox events.
- JPA entities mapped to a module's schema are used only from that module.
- General hygiene: no System.out, no field injection, no generic exceptions.
- A separate check scans native SQL and query strings for references to other modules' schemas, since ArchUnit can't see inside strings.

**Rules for code that crosses modules**

- Modules refer to each other's data by ID only; no JPA relationships across modules.
- Module APIs accept and return their own DTOs, never internal entities.
- Use events where a slight delay is acceptable; use synchronous calls only when an answer is needed immediately.

All of these run in CI on every pull request (NFR-67) within the 10-minute budget (NFR-68).

## Consequences

**What gets easier**

- Boundary violations, including those in AI-written code, fail the build instead of relying on review.
- Module diagrams are generated from the code, so documentation can't drift.
- Extracting a module later is mostly moving code: its schema is already separate, its events can move to Kafka, and its API is already a deliberate contract.

**What gets harder**

- Package layout must follow Modulith's conventions, and exposing extra types from a module's internals needs explicit declaration.
- Cross-module work needs DTOs and IDs instead of convenient entity links.
- ArchUnit rules need maintenance as the design evolves.

**Extraction path:** Identity, Talent Profile, Reference Data, AI Drafting and Moderation can be extracted independently. Gigs, Availability and Hiring share the hire transaction and can only be extracted together. Synchronous calls into an extracted module become network calls with timeouts, retries and authentication; shared transactions would need a saga.

**Outbox:** events that leave the core (to Kafka) go through a custom transactional outbox table and relay, not Spring Modulith's event externalisation. The custom outbox keeps strict per-key ordering (rows relayed in creation order) and full control over the event envelope, Kafka key and headers, which both the Java and Go workers consume as a contract.

**Events between modules inside the core** use Spring Modulith's event publication registry: events are recorded in the database in the same transaction and delivered to in-process listeners after commit, with failed or interrupted deliveries resubmitted. Listeners must be idempotent. Most cross-module work in the core stays synchronous; these events are for work that can happen a moment later.

**What would make us revisit this decision**

- Modulith's conventions repeatedly forcing awkward package structures → move module rules to ArchUnit alone.
- A need for compiler-enforced boundaries (for example, several teams on the codebase) → consider a multi-module build.
