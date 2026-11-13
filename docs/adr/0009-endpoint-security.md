# ADR-0009: Security configured in the Platform module, with each module declaring its own public endpoints

- Status: Proposed
- Date: 2026-11-13
- Related: ADR-0004 (module boundaries), ADR-0005 (authentication), FR-9, FR-12, NFR-33, NFR-34, NFR-35

## Context

The first module (Identity, PR #10) added Spring Security with one configuration class inside the Identity module. That class lists every public path in the application (the three auth endpoints, the health probes, the API docs) and requires a token for everything else.

Later modules need public endpoints of their own: visitors browse open gigs and Talent profiles without logging in (FR-9), while applying and seeing contact details need an account (FR-12). With a central list inside Identity, every such endpoint means editing another module's code, which ADR-0004 is meant to prevent.

Public endpoints are also not the same for everyone: the same "browse gigs" endpoint shows a logged-in user a Poster's first name but a visitor only the locality (NFR-34). So a public endpoint must still identify the caller when a token is sent.

## Options considered

1. **Keep one central list of public paths in Identity**
   - Pro: all public paths visible in one file.
   - Con: every module's public endpoint means editing Identity; security that covers the whole application lives in one feature module.
2. **A central list in the Platform module**
   - Pro: security lives in the cross-cutting module.
   - Con: still one file every module must edit.
3. **Security configured in Platform; each module declares its own public endpoints**
   - Pro: adding a public endpoint never touches another module; Platform collects the declarations, so the full list is still available in one place at runtime (and in a test).
   - Con: public paths are spread across modules, so reviewers must look for the declarations.
4. **An annotation on public controller methods** (e.g. `@PublicEndpoint`)
   - Pro: the rule sits next to the endpoint.
   - Con: needs scanning of handler methods at startup and is easier to miss in review than an explicit declaration; harder to express method-and-path pairs consistently.

## Decision

We use **option 3**.

- **The security configuration lives in the Platform module**: stateless, bearer JWT verification as in ADR-0005, and every request authenticated unless declared public.
- **Each module declares its public endpoints** as a bean of a small Platform type (`PublicEndpoints`) listing HTTP method and path patterns, relative to the `/v1` prefix. Platform collects all such beans into the security configuration. Infrastructure paths (health probes, API docs where enabled) are declared by Platform itself.
- **Public means "no token required", not "anonymous only".** On a public endpoint, a request without a token is handled as a visitor; a request with a valid token is identified as that account; an invalid or expired token is rejected with 401 rather than silently treated as a visitor. Each module then applies the visibility rules (NFR-34) based on who is calling.
- **Visibility is enforced in the module that owns the data**, in its responses, never only by hiding fields in a client.
- **A test lists every public endpoint** in the application, so adding one shows up as a change in that test's expectations and is reviewed deliberately.

## Consequences

**What gets easier**

- Modules add public endpoints without touching other modules' code.
- Security that applies to the whole application lives in the cross-cutting module.
- One endpoint can serve visitors and logged-in users with different visibility.

**What gets harder**

- Public paths are declared in several places; the listing test is what keeps them visible.
- Optional authentication needs care: code must handle "no caller" explicitly, and must never show more to a visitor because a token was missing.

**What we now have to do**

- Move the security configuration from Identity to Platform, add `PublicEndpoints`, and have Identity declare its three auth endpoints.
- Add the public-endpoint listing test.
- Add the rule to `core/CLAUDE.md`: public endpoints are declared in the owning module, never by editing the security configuration.

**What would make us revisit this decision**

- Needing per-role authorization (for example Admin endpoints): extend the same mechanism with role requirements, or adopt method-level security, in a new ADR.
