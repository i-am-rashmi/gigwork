# GigWork — Components and Flows

Status: Draft · 2026-11-12 · Builds on *GigWork — Requirements* (FRs and NFRs referenced by number)

This doc breaks the requirements into components and walks through how they work together in each user journey. It is the input for the data model and the Phase 1 ADRs.

## Principles

- **Group by the data owned.** A component owns its data; no other component reads or writes it directly.
- **One owner per rule.** Each important rule lives in exactly one component (for example, only Gigs changes a gig's status; only Availability decides whether a Talent is free).
- **Synchronous only when an answer is needed now.** Inside the core service, a synchronous call is a method call and can share the database transaction. Everything else is an event, written through the outbox and delivered via Kafka.
- **Never trust the client.** The UI is for convenience; the server validates every input and checks ownership on every request.
- **One event, many consumers.** State-change events serve both matching and analytics. Activity signals (views, searches) go separately and directly to analytics.

## Components

All components except Matching & Notifications are modules inside the core service (a modular monolith, ADR-0001).

| # | Component | Owns | Requirements | Phase |
| --- | --- | --- | --- | --- |
| 1 | Identity & Access | Accounts, OTP codes, sessions, verified mobile and email (encrypted), contact methods | FR-1, FR-2, FR-3, FR-3a | 1 |
| 2 | Talent Profile | Profiles, categories (up to 5), location point, radius, travel flag, portfolio links | FR-4 to FR-6, FR-8 | 1 |
| 3 | Availability | Manual busy blocks and confirmed hire bookings; the only owner of "is this Talent free?" and of the double-booking rule | FR-7, FR-29 | 1 |
| 4 | Reference Data | Categories, cities, zones; Admin-managed, read by everyone, cached | FR-33, FR-34 | 1 (seed data) |
| 5 | Gigs | Gigs and their lifecycle; the only component that changes a gig's status | FR-15, FR-16, FR-18, FR-19, FR-31 | 1 |
| 6 | AI Drafting | LLM calls, prompt versions, masking, drafting sessions; hands drafts back, never publishes | FR-13, FR-14, FR-17 | 1 |
| 7 | Hiring | Applications, shortlists, consent records, contact access log, hires | FR-23 to FR-28, FR-25a | 1 |
| 8 | Matching & Notifications (separate service) | Its own copy of Talent data, in-app inbox, notification records, delivery | FR-20 to FR-22, FR-24, FR-30, FR-32 | 1 (inbox, end of phase), 2 (fan-out) |
| 9 | Discovery | Browse and search | FR-9 to FR-12 | 1 (plain list), 3 (full) |
| 10 | Platform | Feature flags, audit log, outbox relay, rate limits, the scheduling configuration and the job lock (ShedLock) | FR-36 and many NFRs | 1 |
| 11 | Moderation | Reports, suspensions | FR-35, NFR-41 | 3 |

**Gig lifecycle (owned by Gigs):** OPEN → FILLED (on a confirmed hire), CANCELLED (by the Poster) or EXPIRED (start time passed). There is no DRAFT status: drafts are not stored on the server (FR-17; saved drafts are future scope F-7).

## Events

| Event | Produced by | Consumed by | Purpose |
| --- | --- | --- | --- |
| GigPublished, GigUpdated, GigClosed | Gigs | Matching & Notifications, analytics | Fan-out; stop notifying for closed gigs; metrics |
| TalentProfileChanged | Talent Profile | Matching & Notifications | Keep the worker's copy current (NFR-18) |
| AvailabilityChanged | Availability | Matching & Notifications | Don't notify busy Talent |
| ApplicationSubmitted | Hiring | Analytics; later the Poster's application summary (FR-24) | Metrics; summaries |
| TalentShortlisted, HireStarted, HireRecorded | Hiring | Matching & Notifications, analytics | One-to-one notifications; metrics |
| AccountDeletionRequested, AccountErased | Identity | Every component | Hide and cancel immediately; erase or anonymise after 30 days |

Events are keyed so that ordering holds where it matters: gig events by gig ID, Talent events by Talent ID. Every event carries the fields its consumers need (category, zone, work mode), so consumers never query the core's database.

## Flows

### 1. Sign up and log in

All inside Identity, all synchronous (the user is waiting). Platform checks OTP limits; Identity sends the code, verifies it, creates or finds the account by the keyed hash of the mobile number, and issues a 15-minute access token plus a refresh token. The only external call is the SMS provider, with email OTP as the fallback for verified emails.

### 2. A Talent creates a profile and marks busy dates

Talent Profile validates categories and the location against Reference Data (synchronous: it can't save without the answer), then saves the profile and a TalentProfileChanged outbox row in one transaction. Busy dates go to Availability, which saves them with an AvailabilityChanged event. Contact details never enter Talent Profile; it holds only the account ID.

### 3. A Poster posts a gig with the form

Gigs validates the category, work mode and location (Reference Data), Platform checks new-account limits, and Gigs saves the gig as OPEN with a GigPublished outbox row in one transaction. Publishing never waits for matching.

### 4. A Poster posts a gig with AI

AI Drafting checks the daily limit, masks contact details, fetches the live category list, and calls the LLM with a 10-second timeout. Output is validated against a schema (retry once, then fall back to the form). Missing details become questions to the Poster, never guesses. Drafts are returned to the Poster's session, not stored; publishing goes through flow 3. AI Drafting never publishes. The drafting session is logged for 14 days.

### 5. A Talent finds a gig and applies

Discovery lists open gigs from Gigs. On apply, Platform checks the daily limit; Hiring checks with Gigs that the gig is still open, that the gig's category is on the Talent's profile, and that it isn't their own gig; a busy-date overlap only shows a warning. The database allows one application per Talent per gig. Posters get a periodic summary of new applications rather than one notification each.

### 6. A Poster shortlists; contacts are revealed with consent

Hiring checks ownership and limits, then records the shortlist with the Poster's share choice. The Talent sees it in the Shortlisted tab and may accept to reveal their own contact. Every contact view re-checks consent (strongly consistent) and fetches the details fresh from Identity: contacts are never copied, so revoking access is instant. Either side can change or withdraw consent; every view is logged for 1 year.

### 7. A hire (the most important flow)

Either side starts a hire; the other must confirm within 24 hours or it lapses. On confirmation, one transaction: Gigs sets the gig to FILLED only if it is still OPEN; Availability books the Talent for the gig's dates, with the database rejecting any overlapping confirmed hire; Hiring marks the application hired; outbox rows record HireRecorded, GigClosed and AvailabilityChanged. If any step fails, nothing changes. Two simultaneous hires for the same Talent and dates: exactly one succeeds. Manual busy blocks don't block a hire.

### 8. Things that happen by themselves

Periodic jobs, each owned by the module whose data it changes (ADR-0010). A job is a conditional SQL statement that sweeps the rows that are due, so it is safe to run twice at the same time and correctness never depends on a timer or a lock; there are no per-item timers and no table of scheduled actions. The jobs are: gig expiry (Gigs, every minute), pending-hire lapse after 24 h (Hiring, every minute), deletion of expired OTP challenges and old sessions, of AI drafting sessions after 14 days, and of accounts after the deletion period (each daily). Platform owns the scheduling configuration, the switch that turns jobs off, and ShedLock, which makes one core instance run a job per tick. Later jobs, such as reminders and application summaries, follow the same rules. A request never relies on a job having run: applying or editing a gig checks the start time itself.

### 9. Publishing a gig notifies matching Talent (Phase 2)

The outbox relay sends GigPublished to Kafka. The separate worker keeps its own copy of Talent data from profile and availability events, runs the matching query on that copy, and creates in-app inbox records in batches with a unique key per (gig, Talent, channel). Email and push are handed to a separate delivery step, one attempt each. A crash restarts the gig's fan-out; the unique key skips those already notified. Targets: first notification within 5 s, 9,000 within 15 s, 30,000 remote within 60 s.

### 10. A user deletes their account

After a fresh OTP, Identity ends sessions and publishes AccountDeletionRequested. Each component handles its own data immediately: profile hidden, open gigs cancelled (shortlisted Talent told), applications withdrawn, contact reveals ended, upcoming hires cancelled with the other side notified. For 30 days the user can log in to restore the account. After 30 days, AccountErased triggers erasure or anonymisation in every component; backups expire within 7 days after that.

## Notification plan

- **Walking skeleton (early Phase 1):** no notifications. Talent see the Shortlisted tab; Posters see pending hires on the gig.
- **End of Phase 1:** a minimal Matching & Notifications worker with the in-app inbox, handling one-to-one messages (shortlisted, hire waiting for confirmation). This builds the outbox → Kafka → worker pipeline on simple messages first.
- **Phase 2:** matching and fan-out added to the same worker.

The in-app inbox is the reliable channel and the source of truth for notifications; email and push only point users to it.

## Open design questions (for the ADRs)

- Who serves the inbox API: the worker (owns the data) or the core (keeps all user-facing APIs in one place)?
- Primary datastore (SQL vs NoSQL), decided after the data model.
- How module boundaries inside the core are enforced.
- Feature flags: a simple database table, or a tool such as Unleash or the OpenFeature standard.
- Recheck ADR-0001 against these components (payments is now parked under monetisation readiness).
