# ADR-0010: Scheduled jobs are idempotent SQL sweeps, run by one instance at a time through ShedLock

- Status: Accepted
- Date: 2026-11-14
- Related: ADR-0002 (datastores), ADR-0004 (module boundaries), ADR-0008 (persistence convention), FR-17, FR-27, NFR-47

## Context

Some changes in GigWork happen because time passes, not because someone sends a request:

| Job | Frequency | Owner |
|---|---|---|
| Expire open gigs whose start time has passed | every minute | Gigs (slice 5) |
| Lapse a pending hire after 24 hours | every minute | Hiring (slice 8) |
| Delete expired OTP challenges and old sessions | daily | Identity (NFR-47) |
| Delete AI drafting sessions after 14 days | daily | AI Drafting (FR-17) |
| Erase accounts after the deletion period | daily | Identity (FR-3a, later) |

The core runs as two or more instances (rolling deploys, availability target). A plain `@Scheduled` method runs on every instance, so every job would fire on all of them at the same moment, over the same rows.

Two separate questions follow:

1. **Correctness:** is the result right if a job runs twice at the same time?
2. **Efficiency:** is the duplicate work acceptable?

## Options considered

1. **Every instance runs every job (`@Scheduled` only), relying on idempotent SQL**
   - Pro: nothing to add.
   - Con: duplicate work that grows with the number of instances; instances compete for the same rows.
2. **`@Scheduled` plus ShedLock: one instance per run, through a lock row in PostgreSQL**
   - Pro: a small, widely used library; no new infrastructure (one table); one run per tick; a lock expires on its own if its holder crashes.
   - Con: one more dependency. A lock timeout shorter than a run would allow a second, overlapping run, so the lock alone must not be what keeps data correct.
3. **A job library with its own tables (db-scheduler, JobRunr, Quartz)**
   - Pro: retries, run history, dashboards, timers per item ("lapse hire X at 14:32").
   - Con: far more machinery than a handful of periodic sweeps need; another set of tables and concepts.
4. **An external scheduler (Kubernetes CronJob, cloud scheduler) starting a process or calling an endpoint**
   - Pro: scheduling leaves the application.
   - Con: depends on infrastructure not chosen yet; harder to run and test locally.

## Decision

We use **option 2 on top of option 1's rule**:

1. **Correctness comes from the SQL, never from the lock.** Every job is a conditional update or delete that is safe to run twice at the same time, for example:

   ```sql
   UPDATE gigs.gig SET status = 'EXPIRED', closed_at = :now
   WHERE status = 'OPEN' AND start_at <= :now;
   ```

   A row that has already changed no longer matches, so each row changes exactly once. Events caused by the change are written in the same transaction (outbox or event publication registry), so they are also written once (ADR-0004, ADR-0008).

2. **ShedLock is an optimisation:** it makes one instance do the work per tick. Its table lives in the `platform` schema. `lockAtMostFor` is set well above a run's expected duration, and `lockAtLeastFor` a little below the interval, so a fast run on one instance isn't immediately repeated by another.

3. **Jobs sweep for due rows; there are no per-item timers.** "Every minute, find what is due" needs only an index on the due time, typically a partial index on the open rows (e.g. `start_at WHERE status = 'OPEN'`), so a sweep stays fast however many closed rows accumulate.

4. **Large sweeps work in batches** (e.g. 500 rows per transaction, selecting with `FOR UPDATE SKIP LOCKED`), so a long job never holds locks that block user requests.

5. **Each module owns its jobs** (`@Scheduled` methods in the module, in a `jobs` sub-package); Platform owns the scheduling configuration, the ShedLock setup and the switch to turn jobs off.

6. **Time comes from the injected `Clock`.** Requests never rely on a job having run: anything that must be exact checks the time itself (e.g. applying requires `start_at > now`, even if the expiry job hasn't caught up yet).

## Consequences

**What gets easier**

- Jobs are short, plain SQL statements with one clear rule: "safe to run twice at once".
- No new infrastructure; jobs run the same way on a laptop, in CI and in production.

**What gets harder**

- A state change can lag its due time by up to one interval (a gig shows as open for up to a minute after its start). Request-time checks cover anything that must be exact.
- ShedLock's timing settings must be chosen per job.
- No built-in run history: each run logs what it did and records a metric (rows changed, duration).

**What we now have to do**

- Add ShedLock (JDBC provider) and a Flyway migration for its table in `platform`.
- Add a property (`gigwork.jobs.enabled`) to turn scheduling off, used by most tests, so tests call job methods directly and deterministically.
- For every job, a test that runs it twice in parallel and checks each row changed exactly once.
- Add the rules to `core/CLAUDE.md`.

**What would make us revisit this decision**

- Jobs that need per-item timing, retries with backoff or run history: consider a job library (option 3).
- Moving to an orchestrator where scheduled jobs belong outside the application: option 4.
