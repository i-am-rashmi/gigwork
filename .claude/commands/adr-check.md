---
description: Review the current branch's changes against the ADRs and design docs
---

Review the changes on the current branch against `main` (`git diff main...HEAD`, plus uncommitted changes).

Check them against every ADR in `docs/adr/`, `core/CLAUDE.md`, and the relevant parts of `docs/design/`. In particular:

- module boundaries: internal packages used across modules, cross-module JPA relationships, cross-module SQL
- events leaving the core without going through the outbox
- invariants enforced in code (check-then-act) instead of in the database
- migrations that edit an existing file, aren't backward compatible, or use unqualified table names
- personal data in logs; contact details copied outside Identity
- naming (Talent and Poster)
- missing tests, especially concurrency tests for invariants

Report findings as a list, each with file and line, the rule or ADR it breaks, and a suggested fix. Separate real problems from suggestions. Don't change any files.
