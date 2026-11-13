# GigWork

A marketplace connecting creative and artistic talent in India (photographers, makeup and mehendi artists, DJs, emcees and more) with people hiring them. Built design-first: the requirements and architecture decisions are written down, and code follows them.

## Read before working

- `docs/prd.md`: problem statement, functional requirements (FR-x), non-functional requirements (NFR-x), phases
- `docs/design/components-and-flows.md`: the 11 components and how they interact
- `docs/design/data-model.md`: entities, access patterns and invariants per component
- `docs/adr/`: accepted architecture decisions (index in `docs/adr/README.md`)

When a task refers to an FR or NFR, read it in `docs/prd.md` first. When a change touches architecture, check it against the ADRs.

## Hard rules

1. **Never create, edit or delete files in `docs/adr/`.** ADRs are written by the owner. If a change seems to need a new or different decision, stop and say so.
2. **Never change the requirements or design docs** unless the task explicitly asks for it.
3. **Every change happens on a branch and goes through a pull request.** Never commit to `main`, never merge a PR, never force-push.
4. **Plan before code.** For any feature or multi-file change, present a plan first and wait for approval.
5. **Tests are part of the change.** New behaviour comes with tests; a task is done only when `./mvnw verify` passes in `core/`.
6. **Don't weaken guarantees to make tests pass.** Never delete or skip a failing test, relax a database constraint, or disable a check without asking.
7. **Ask when the docs don't answer it.** If the requirements are ambiguous or silent, ask rather than guess.

## Language

- The two roles are **Talent** (offers a service) and **Poster** (publishes a gig). Use these words everywhere: code, tables, APIs, messages. Never "worker", "hirer", "doer", "freelancer" or "client" for these roles.
- A **gig** is one piece of work. A **hire** links one Talent to one gig. A **booking** is the time a confirmed hire reserves.

## Architecture in one paragraph

A modular-monolith **core** (Spring Boot, `core/`) plus a separate **Matching & Notifications worker** (`worker/`, later; also a Go version in `worker-go/` for a benchmark). One PostgreSQL instance with PostGIS: a database per service, a schema per core module. Redis for rate limits. Kafka between the core and the worker; events leave the core only through the transactional outbox. A path-based router sits in front of both services. See ADR-0001 to ADR-0007.

## Stack

Java 21 (pinned in `.sdkmanrc`), Spring Boot, Maven (`./mvnw`), Spring Data JPA, Flyway, Spring Modulith, ArchUnit, Testcontainers, PostgreSQL 17 + PostGIS, Redis, Kafka.

## Commands

```bash
docker compose -f infra/docker-compose.yml up -d     # local Postgres, Redis, Kafka
cd core && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # run the core locally
                                                      # (fake keys, OTP codes logged on gigwork.dev.otp, Swagger UI at /swagger-ui.html)
cd core && ./mvnw verify                              # build + all tests (what CI runs)
cd core && ./mvnw test -Dtest=ClassName               # one test class
```

Docker must be running for tests (Testcontainers).

## Git

- Branch names: `feat/<short-name>`, `fix/<short-name>`, `chore/<short-name>`, `docs/<short-name>`.
- Commit messages follow Conventional Commits: `feat(hiring): confirm a pending hire`.
- PR descriptions list the FRs/NFRs covered, what was tested, and anything left open.

## Working style

- Prefer small, reviewable changes: one feature or fix per branch.
- Explain non-obvious decisions in the PR description, not in long code comments.
- Keep personal or machine-specific notes in `CLAUDE.local.md` (git-ignored).
