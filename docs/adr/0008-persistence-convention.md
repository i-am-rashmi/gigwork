# ADR-0008: JPA by default, single native SQL statements for writes that enforce invariants

- Status: Proposed
- Date: 2026-11-13
- Related: ADR-0002 (datastores), ADR-0003 (data ownership), ADR-0004 (module boundaries), NFR-14, NFR-16, NFR-37, NFR-70

## Context

The first module (Identity, PR #10) was built with Spring's JdbcClient and hand-written SQL for every repository. That worked well for its most important code: statements such as a conditional attempt counter (`UPDATE ... WHERE attempts < 5 RETURNING ...`), single-statement refresh-token rotation, and `INSERT ... ON CONFLICT DO NOTHING`. But it is a project-wide choice: early patterns are copied into every later module, including by the AI coding agent.

The data model has two kinds of database work:

- **Ordinary reads and writes** of entities: profiles, gigs, applications, categories. Most of the code.
- **Writes that enforce invariants under concurrency:** conditional status changes (a gig filled only if still open), counters with limits, rotations, idempotent inserts. ADR-0002 requires these to be enforced by the database, never by check-then-act code.

The schema is owned by Flyway; Hibernate only validates it (`ddl-auto: validate`).

## Options considered

1. **Plain SQL everywhere (JdbcClient)**
   - Pro: every statement is explicit; the atomic statements are natural.
   - Con: a lot of hand-written mapping and boilerplate for ordinary CRUD; no entity mapping validated against the schema.
2. **JPA everywhere, including invariant-enforcing writes**
   - Pro: one style.
   - Con: JPA's normal load-modify-save is a check-then-act sequence: two requests can load the same row and both save. Optimistic locking (`@Version`) turns this into failures and retries rather than a single correct statement, and some statements (`RETURNING`, `ON CONFLICT`) have no JPA equivalent.
3. **JPA by default, with single native SQL statements for invariant-enforcing writes**
   - Pro: concise code for the common case, Hibernate validation of mappings against the Flyway schema, and correct atomic statements where correctness depends on them.
   - Con: two styles in one codebase; developers (and the agent) must recognise which case they are in.

## Decision

We use **option 3** in every module of the core, including Identity, which is converted now.

- **Entities and Spring Data JPA repositories** for ordinary reads and writes. Entities map to their module's schema (`@Table(schema = "...")`) and are used only inside that module.
- **Any write that enforces an invariant is a single native SQL statement** in the module's repository (a `@Modifying @Query(nativeQuery = true)` method, or JdbcClient where JPA can't express the statement, such as `UPDATE ... RETURNING`). This covers conditional status updates, counters with limits, token rotation, `ON CONFLICT` inserts, and anything else whose correctness depends on concurrent requests. It is never written as load-modify-save.
- **Uniqueness, exclusion and check constraints stay in the database**, defined in Flyway migrations, regardless of what JPA annotations say.
- **No JPA relationships across modules** (ADR-0004): other modules' data is referenced by ID only. Within a module, relationships are lazy by default.
- **Every invariant-enforcing statement has a concurrency test** that runs simultaneous requests against real PostgreSQL.

## Consequences

**What gets easier**

- Less boilerplate for ordinary data access; mappings are validated against the schema at startup.
- The invariants that matter most remain single, atomic statements enforced by PostgreSQL.
- One clear rule for reviewers and for the agent: "does this write enforce an invariant? Then it is one native statement with a concurrency test."

**What gets harder**

- Two styles in one codebase.
- Usual JPA pitfalls must be watched: N+1 queries, lazy loading outside transactions (open-in-view is off), and entities that drift from the Flyway schema (caught by validation).
- Mixing JPA and native updates in one transaction needs care: native updates bypass the persistence context, so entities already loaded may be stale (clear or refresh them, or don't load them first).

**What we now have to do**

- Convert Identity's repositories: entities and Spring Data for accounts, mobile identities and sessions; keep the attempt counter, single-use marking, refresh rotation and conflict-tolerant inserts as native statements.
- Add the rule to `core/CLAUDE.md`.

**What would make us revisit this decision**

- If most repositories end up being native statements anyway, return to plain SQL everywhere for a single style.
- If JPA's overhead shows up in load tests on hot paths, use native queries for those reads.
