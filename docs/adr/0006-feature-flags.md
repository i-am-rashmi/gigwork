# ADR-0006: Feature flags through OpenFeature, backed by our own PostgreSQL provider

- Status: Proposed
- Date: 2026-11-13
- Related: FR-36, NFR-49, NFR-50, NFR-51, ADR-0002, ADR-0003, Monetisation readiness (parked)

## Context

An Admin must be able to turn features on or off without a release, globally or per city, and later per Talent tier (FR-36). Changes must apply within one minute with no deploy (NFR-49); if flags can't be loaded, every flag falls back to a safe default (NFR-50); every change is audit-logged (NFR-51).

Both services need flags: the core and the Matching & Notifications worker, which will exist in Java and in Go.

Flags come in different kinds with different lifetimes: release toggles (short-lived, removed after launch), kill switches (long-lived, operational) and per-city rollouts. Entitlements such as per-tier limits are business configuration, not flags.

## Options considered

1. **Our own flag table in PostgreSQL with an in-memory cache and an Admin API**
   - Pro: no new infrastructure; meets every NFR directly; full control.
   - Con: we build the API, targeting rules and audit; no ready-made UI.
2. **Unleash, self-hosted**
   - Pro: admin UI, gradual percentage rollouts, SDKs for Java and Go.
   - Con: another container and database in the laptop stack and every ephemeral environment.
3. **A SaaS flag service** (LaunchDarkly, ConfigCat, Flagsmith Cloud)
   - Pro: polished, with free tiers.
   - Con: external dependency, data leaving the system, cost later.
4. **Code against the OpenFeature standard API, starting with our own PostgreSQL-backed provider**
   - Pro: the simplicity of option 1 plus vendor neutrality: feature code calls only the standard API, so the provider can later be swapped for Unleash or a SaaS tool without changing it.
   - Pro: every evaluation passes a default value, which gives the safe fallback of NFR-50.
   - Con: a small amount of extra structure (writing a provider).

### Distributing flags to the worker

1. **The worker polls a core endpoint**
   - Pro: simple.
   - Con: couples the worker to the core being available.
2. **Flag changes published as events on a compacted Kafka topic**
   - Pro: the worker keeps flags in memory and rebuilds them on restart by reading the topic from the start, the same pattern as the Talent copy (ADR-0003); no runtime dependency on the core.

## Decision

- **Feature code uses the OpenFeature API** in both the core (Java) and the worker (Java and Go), always passing a safe default and an evaluation context (account ID, city, and later tier).
- **Flags are stored in the Platform module** (platform.feature\_flag: key, kind, default, per-city overrides, owner, removal date, updated by, updated at), changed through an Admin API, with every change written to the audit log.
- **Each core instance caches flags in memory** and refreshes them at least every 30 seconds, meeting the one-minute target.
- **Flag changes are published through the outbox to a compacted feature-flags topic**, keyed by flag name, from which the worker builds its in-memory copy.
- **Hygiene:** every release toggle has an owner and a removal date; expired release toggles are reported in CI. Entitlements and tier limits are kept out of flags.

## Consequences

**What gets easier**

- Features can be switched on, off or per city without a release, in both services.
- If the flag store or topic is unavailable, defaults keep the core flows working.
- Moving to Unleash or a SaaS tool later only replaces the provider: flag definitions are migrated once, evaluation-context attributes stay the same, and the Kafka flags topic is retired; feature code is unchanged.

**What gets harder**

- We build and test the provider, the Admin API and per-city targeting ourselves.
- No percentage rollouts or experiment analytics until a richer provider is adopted.
- Flag debt must be actively managed.

**What would make us revisit this decision**

- Needing gradual percentage rollouts, user-segment targeting or experiments → adopt Unleash or a SaaS provider behind OpenFeature.
- A growing number of flags making the home-grown Admin API a burden → same.
