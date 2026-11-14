# GigWork — Data Model

Status: Draft · 2026-11-12 · Builds on *GigWork — Requirements* and *GigWork — Components and Flows*

The logical data model per component: entities, access patterns, guarantees and volumes. It is deliberately database-neutral (entities and fields, not tables and columns); the datastore choice follows from it in an ADR.

## Conventions

- IDs are random UUIDs (NFR-33). Components refer to each other's data only by ID.
- Secrets are stored hashed (OTP codes, refresh tokens); contact details are encrypted, with a keyed hash for lookups (NFR-44).
- Locations are coordinates only, never street addresses (NFR-35).
- Nothing referenced elsewhere is hard-deleted: reference data is retired, gigs are cancelled, and personal data is erased or anonymised on account erasure (FR-3a).

## 1. Identity & Access

| Entity | Key fields |
| --- | --- |
| Account | account ID, first name, status (active / deletion requested / erased), created at, deletion requested at |
| Mobile identity | account ID, mobile (encrypted), mobile hash, verified at |
| Email identity | account ID, email (encrypted), email hash, verified at (empty until verified) |
| Contact methods | account ID, channels shared (WhatsApp / call / email), using only the verified mobile and email |
| OTP challenge | ID, target hash, code (hashed), purpose (login / verify email / step-up / delete), expires at, attempts, used at |
| Session | ID, account ID, refresh token (hashed), created at, last used at, revoked at |

**Access patterns:** find account by mobile hash (login); count recent OTPs per number and IP (rate limits); verify OTP by ID with an attempt increment; find session by token hash (every \~15 minutes per active user); read contact methods by account ID (consented reveals); find accounts by deletion date (daily erasure job).

**Guarantees:** one account per mobile number (and per verified email); the OTP attempt counter is updated atomically so 5 attempts really means 5; revoked sessions stay revoked.

**Volume:** about 300,000–500,000 accounts at design scale.

## 2. Talent Profile

| Entity | Key fields |
| --- | --- |
| Talent profile | account ID (one per account), profile type (individual / group), member count, headline, bio, home location (coordinates), city ID, zone ID, locality name, service radius (1–50 km), willing to travel, status (active / hidden), updated at |
| Talent category | account ID, category ID (up to 5) |
| Talent specialisation | account ID, specialisation ID (up to 10 in total) |
| Talent language | account ID, language code (optional) |
| Portfolio link | ID, account ID, URL, type, display order |
| Preferred zone | account ID, zone ID (default browse filter only) |

Name and contact details live in Identity. A home location outside every zone is assigned the nearest city and zone for browsing; matching uses the actual coordinates.

**Access patterns:** read a profile by account ID; read many profiles by a list of IDs (applicant review); check "does Talent X have category Y" (applying) and, for a gig with a specialisation, "does Talent X have specialisation Z"; in Phase 3, filter by category and zone and sort by distance. Matching never reads here; it uses its own copy.

**Guarantees:** at most 5 categories and at most 10 specialisations even under concurrent edits; categories, specialisations and languages must be live; every specialisation belongs to one of the Talent's categories (removing a category removes its specialisations); one profile per account.

**Volume:** 50,000 profiles per city; under a million rows in total.

## 3. Availability

| Entity | Key fields |
| --- | --- |
| Busy block | ID, Talent account ID, start, end, all-day flag, created at (private to the Talent) |
| Booking | ID, Talent account ID, gig ID, start, end (the gig's times), status (active / cancelled), created at |

Busy blocks and bookings are separate because they follow different rules: bookings must never overlap each other, while busy blocks are hints used for matching and warnings and never block a hire.

**Access patterns:** insert a booking with an atomic overlap check (hire confirmation); check whether anything overlaps a time range for one Talent (apply warning); list a Talent's blocks and bookings in a date range (calendar); in Phase 3, check many Talent for one date (search).

**Guarantees:** no two active bookings for one Talent overlap, enforced by the database even under simultaneous requests (NFR-14, FR-29); end after start.

**Volume:** about 2.5 million rows at design scale.

## 4. Reference Data

| Entity | Key fields |
| --- | --- |
| Category | ID, name, slug, status (live / retired), allowed work modes, language relevant (yes / no), display order |
| Specialisation | ID, category ID, slug, name, status (live / retired), display order |
| Language | code (ISO 639), name, status (live / retired), display order |
| City | ID, name, status (live / not live) |
| Zone | ID, city ID, name, boundary (a shape on the map) |
| Locality | ID, source ID, name, PIN code (may be empty), city ID, zone ID, centre point (seeded from a public dataset) |

**Access patterns:** read everything, cached; find the zone containing a point; find the nearest zone for points outside every zone; search localities by name or PIN as the user types; rare Admin edits.

**Guarantees:** unique names and slugs (a specialisation's slug is unique within its category); a locality's source ID is unique within its city, while locality names are not unique; every locality has a zone (the one containing its centre point, else the nearest); a city's zones don't overlap; nothing referenced is deleted, only retired.

**Volume:** a few thousand rows.

## 5. Gigs

| Entity | Key fields |
| --- | --- |
| Gig | ID, Poster account ID, category ID, specialisation ID (optional), language code (optional), title, description, start, end, work mode (on-site / remote), location point, city ID, zone ID, locality name, TA offered, budget minimum, budget maximum, status, published at, closed at, close reason, outcome, gig group ID |
| Gig group | ID, Poster account ID, source (form / AI), AI drafting session ID, created at |

**Lifecycle:** OPEN → FILLED (confirmed hire) or CANCELLED (by the Poster) or EXPIRED (start passed); FILLED → OPEN (hire cancelled before the start) or CANCELLED (Poster cancels; the hire is cancelled too). A filled gig can't be edited.

**Access patterns:** insert one gig, or several in one transaction for an AI group; read by ID; list open gigs newest first (Phase 1); search open gigs by zone, category, date, budget and distance, plus remote gigs everywhere (Phase 3); list a Poster's gigs; conditional status updates; find open gigs whose start has passed (expiry, every minute).

**Guarantees:** end after start, start in the future at publish; on-site gigs have a location, remote gigs have no location or TA, and the category allows the work mode; a specialisation belongs to the gig's category; a language is set only when the category is language-relevant; only valid status changes, using conditional updates; budget not negative.

**Volume:** about 550,000 new gigs a year per city, of which only a small fraction is open at any time. Frequent queries target open gigs, so they must stay fast as closed gigs accumulate.

## 6. AI Drafting

| Entity | Key fields |
| --- | --- |
| Drafting session | ID, Poster account ID, request text (masked), model output, clarifying questions and answers, prompt version, model version, latency, tokens, estimated cost, outcome, created at (deleted after 14 days) |

Prompts and the evaluation set live in the repository, not the database.

**Access patterns:** insert per request; update by ID with answers; count successful drafts per Poster per day (limit 20) and model calls (cap 50); aggregate by prompt and model version; delete sessions older than 14 days.

**Guarantees:** only masked text is stored; every session records its prompt and model version.

**Volume:** a few thousand a day, kept 14 days.

## 7. Hiring

| Entity | Key fields |
| --- | --- |
| Application | ID, gig ID, Talent account ID, note, status (active / withdrawn), applied at, busy warning shown |
| Shortlist entry | application ID, shortlisted at, removed at, Poster shares contact, Talent consent given at, consent withdrawn at |
| Hire | ID, gig ID, application ID, started by (Poster / Talent), status (pending / confirmed / lapsed / declined / withdrawn / cancelled), started at, confirmed at, cancelled at, cancelled by, cancel reason, booking ID |
| Contact access log | ID, viewer account ID, viewed account ID, gig ID, viewed at (kept 1 year) |

**Access patterns:** insert an application and count a Talent's applications today (limit 30); list applications by gig (Poster) and by Talent (dashboard tabs); shortlist with counts per gig (10) and per Poster per day (20); check shortlist and consent by gig, viewer and viewed account on every contact view, then log it; insert a pending hire; find pending hires older than 24 hours (lapse job); shortlists and hires per Poster over time (abuse detection).

**Guarantees:** one application per Talent per gig; at most 10 shortlisted per gig, even under concurrency; contacts visible only while shortlisted and consented; at most one pending hire per gig at a time; at most one confirmed hire per gig (together with the conditional FILLED update in Gigs).

**Volume:** about 15,000 applications a day per city, around 5 million a year per city; the largest growing dataset in the core. Kept for history, anonymised on account erasure.

## 8. Matching & Notifications (separate service)

| Entity | Key fields |
| --- | --- |
| Talent match copy | Talent account ID, categories, location point, radius, willing to travel, city ID, status, version |
| Talent busy copy | Talent account ID, busy time ranges (manual blocks and bookings) |
| Fan-out run | gig ID, status (running / done / stopped), started at, finished at, recipient count |
| Inbox item | ID, recipient account ID, type, gig ID, title and link, dedupe key (gig, recipient, type), created at, read at (kept 90 days) |
| Delivery attempt | inbox item ID, channel (email / push), result, at |

The version field ensures an older event processed late never overwrites newer data: the copy only moves forward.

**Access patterns:** upsert the copy by Talent ID when the version is newer; matching by category, radius and availability (on-site) or by category and availability across cities (remote); batch-insert inbox items with duplicates ignored (up to 30,000 in 60 s); read the inbox by recipient, newest first, with an unread count (every app open); mark as read; delete items older than 90 days.

**Guarantees:** no duplicate notification per gig, recipient and type; the copy never moves backwards; users read only their own inbox.

**Volume:** by far the largest dataset: about 3 million inbox items a day per city at peak, kept 90 days, roughly 270 million rows per city.

**Channels:** in-app inbox plus email (verified emails only) until an app exists; push comes with the app.

## 9. Discovery

Owns almost no data: Phase 1 reads open gigs directly from Gigs. Phase 3 may add a search index (a read-optimised copy of gigs and profiles kept current by events) if the main database can't meet NFR-9; decided after measuring.

## 10. Platform

| Entity | Key fields | Access pattern |
| --- | --- | --- |
| Outbox event | ID, type, key, payload, created at, sent at | Inserted in every business transaction; relay reads unsent rows oldest first every \~1 s; sent rows cleaned up after a few days |
| Scheduled action | ID, type, target ID, due at, status | Read due and pending rows every minute with row locking |
| Feature flag | key, default, per-city overrides, updated by, updated at | Read constantly (cached); changes apply within 1 minute |
| Audit log entry | ID, actor, action, target, old and new value, at | Append only; kept 1 year |
| Rate-limit counter | key (user, IP or phone hash plus time window), count | Incremented on every request; expires within minutes |
| Activity signal | type, IDs, at | Sent fire-and-forget to analytics |

## 11. Moderation (Phase 3)

| Entity | Key fields |
| --- | --- |
| Report | ID, reporter, target (gig or profile), reason, status (open / actioned / dismissed), handled by, at |
| Suspension | account ID, reason, by (Admin / automatic), from, until |

## Data shapes across GigWork

The access patterns fall into four distinct shapes, which are the input for the datastore ADR:

| Shape | Where | Needs |
| --- | --- | --- |
| 1. Relational and transactional | Identity, Talent Profile, Availability, Gigs, Hiring, Platform (outbox, scheduled actions) | Multi-entity transactions, uniqueness, no-overlap constraints, conditional updates, joins |
| 2. Geographic | Talent Profile and Gigs (zone lookup, distance), the worker's match copy | Within-radius queries, point-in-zone, nearest zone |
| 3. Huge, append-only, expiring | The notification inbox | Hundreds of millions of rows, burst writes, reads by recipient only, deletion by age |
| 4. Tiny, hot, short-lived | Rate-limit counters, caches | Very fast increments with automatic expiry |
