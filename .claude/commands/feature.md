---
description: Build a feature from the PRD — plan, branch, implement with tests, open a PR
argument-hint: <FR ids or short description, e.g. "FR-1 FR-3 OTP sign-up">
---

Build this feature: $ARGUMENTS

Follow these steps in order.

1. **Understand.** Read the referenced FRs and NFRs in `docs/prd.md`, the relevant flows in `docs/design/components-and-flows.md`, the entities in `docs/design/data-model.md`, and any ADR that applies. List anything ambiguous or missing and ask before going further.

2. **Plan, then stop.** Present a plan and wait for my approval:
   - which modules change, and which public APIs or events are added
   - migrations (tables, constraints, indexes), schema-qualified
   - endpoints (method, path, request/response)
   - the tests you will write, including concurrency tests for invariants
   - anything that seems to conflict with an ADR

3. **Branch.** After approval, create `feat/<short-name>` from an up-to-date `main`.

4. **Implement in small commits** with Conventional Commit messages. Follow `CLAUDE.md` and `core/CLAUDE.md`.

5. **Verify.** Run `cd core && ./mvnw verify` until it passes. Don't skip, delete or weaken tests or constraints to get there.

6. **Report.** Summarise what changed, how it was tested, and anything left open. Draft the PR description (FRs/NFRs covered, tests, open points), then ask me before pushing or opening the PR.
