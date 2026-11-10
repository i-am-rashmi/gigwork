# GigWork — Product Requirements Document

Nov 2, 2026 · @Rashmi · Status: Draft v0.1

## Overview

GigWork connects people who need short-term creative and personal-service work done with nearby, available talent, starting in Bengaluru.

**Problem.** In India, finding a photographer, video editor, makeup artist or mehendi artist for a specific date still runs on Instagram DMs, WhatsApp forwards and word of mouth. Posters cannot see who is free on their date or nearby. Talent misses gigs because there is no reliable way to hear about them, and reputations do not travel beyond personal networks.

**Product.** A portfolio-first marketplace: talent build a profile that shows their work, where they operate and when they are busy. Posters describe what they need in plain language, get structured gig posts split by category, see recommended talent close by, and publish. Matching talent are notified based on category, distance and availability.

**Focus categories.** Photography, videography, video editing, content creation, makeup, mehendi and dog walking. Home repairs and similar services are supported by the taxonomy but are not a focus.

**Dual purpose.** GigWork is a portfolio project demonstrating senior backend engineering (distributed systems, data modelling, applied AI) and a candidate product. Where the two pull apart, this PRD says which one wins.

## Users and personas

Any account can both post gigs and offer talent; a talent profile is an add-on to a basic account, not a separate user type.

| Persona | Example | Needs most | Biggest frustration today |
| --- | --- | --- | --- |
| Talent | Freelance wedding photographer, Jayanagar | Steady nearby gigs on dates they are free; a portfolio that does the selling | Gigs arrive late via forwards; already booked when they see them |
| Poster (individual) | Family planning a mehendi function | Someone good, nearby, free on the date, within budget | Comparing Instagram pages and chasing replies |
| Poster (small business) | Café owner launching a menu | A small team across several skills (shoot, edit, reels) | Does not know which roles the job needs |
| Admin | Platform operator | Taxonomy control, moderation, dispute handling | — |

## Goals, non-goals and success metrics

**Goals**

1. A poster goes from a one-line description to published, correctly categorised gigs in under 2 minutes.
2. Only relevant talent are notified: right category, within their own service radius, not busy at the gig time.
3. A talent can never be booked into two overlapping gigs.
4. The system's behaviour under load and failure is measured and published, not assumed.

**Non-goals (for now)**

- Pan-India launch, native mobile apps, or a fully self-serve payments product before Phase 4.
- Hourly-wage or delivery-style gig work (drivers, riders).
- AI that publishes or ranks without a human or deterministic step in between.

**Success metrics**

| Metric | Target | Measured from |
| --- | --- | --- |
| AI draft category accuracy on the eval set | ≥ 90% | Phase 1 |
| AI drafts that correctly ask for missing date or location | ≥ 85% | Phase 1 |
| Talent search p99 latency on 50k profiles | < 100 ms | Phase 1 |
| Gig publish → last notification enqueued, 3k recipients | < 5 s | Phase 2 |
| Lost or duplicated notifications in failure tests | 0 | Phase 3 |
| Double bookings under concurrent selection tests | 0 | Phase 2 |

The latency and throughput targets are starting assumptions to be revised once the first load test runs.

## Talent profiles

A talent profile is defined by four things: what they do, where they work, when they are busy, and proof of quality.

**Requirements (Phase 1)**

- **Categories:** one or more from a fixed, admin-managed taxonomy (about 15 at launch). Talent cannot create categories. Sub-skills (e.g. Photography → wedding, product, portrait) are not categories for now; the data model leaves room to add them later without a migration of existing gigs.
- **Service area:** a home base (lat/lng, set by map pin, shown publicly only as locality) plus a service radius in km. "Willing to travel" extends matching beyond the radius, optionally with a travel fee note.
- **Availability:** talent are available by default and mark *busy blocks* (date-time ranges). Selection for a gig creates a busy block automatically. A recurring weekly unavailability (e.g. "no Mondays") is supported.
- **Portfolio:** images, video links (YouTube, Instagram, Drive) and short project descriptions, uploaded via presigned URLs; image thumbnails generated asynchronously.
- **Rates:** indicative price range per category (e.g. ₹15,000–40,000 per wedding day). Optional, not binding.
- **Profile completeness score** used in ranking and shown to the talent as a nudge.

**Later**

- Verified identity badge (Phase 4), reliability score (Phase 4), Google Calendar sync for busy blocks (backlog).

**Acceptance**

- A profile with no busy blocks is matched for any date.
- Exact coordinates never appear in any API response visible to another user.

## Search and discovery

Anyone, signed in or not, can search talent and open gigs; contacting or applying requires an account.

**Talent search filters:** category, distance from a chosen point (default 10 km), available on a date or date range, price range, minimum rating (Phase 4), willing to travel.

**Gig search filters:** category, distance, date range, budget range, status (open by default).

**Ranking (Phase 1):** a deterministic score combining distance, profile completeness and recency of activity. Weights live in config so they can be tuned without a deploy. Phase 3 adds embedding similarity as one more signal, measured against this baseline.

**Results** show locality and distance, never exact location. Pagination is cursor-based.

**Implementation note:** Postgres full-text search plus PostGIS covers the expected scale. A dedicated search engine is only introduced if a measured query shows Postgres falling short.

## Gig lifecycle

A gig moves through six states, and only the transitions shown are allowed; the state machine is enforced in the domain layer and every transition is an audited event.

&#91;embedded content: gig lifecycle · 6 states\]

**Requirements**

- **Post (Phase 1):** manual form or AI drafts. Required: category, title, description, schedule, location, budget range. A schedule is a single date-time, a time window, or a span of several days; availability matching and busy blocks check every part of it.
- **Close:** a poster can withdraw an Open gig (Cancelled). Open gigs with no selection move to Expired automatically when the start time passes.
- **Edit:** allowed while Open; changes to date, time or location notify existing applicants.
- **Gig group:** gigs created from one AI request share a group id so the poster can manage them together.
- Every transition is idempotent and records who triggered it and when.

Draft also covers AI drafts the poster has not yet published; unpublished drafts are deleted after 14 days.

## AI gig drafting and recommendations

The AI turns one free-text request into reviewable gig drafts, one per category; it never publishes and never ranks talent.

**Flow**

1. Poster types a request, e.g. "Looking for people to help with videography, editing and content for our café launch on 14 Dec in Indiranagar." English, Hindi, Kannada and mixed-language input are accepted.
2. The LLM returns structured output constrained by a JSON schema: an array of drafts, each with `category` (enum from the taxonomy), title, description, date/time window, location text, budget range, required skills, and a list of `missing_fields`.
3. If date, location or budget is missing, the UI asks the poster rather than the model guessing.
4. The poster edits, removes or adds drafts, then publishes them together as a linked **gig group** (one request, several gigs).
5. For each draft, the backend shows recommended talent: SQL filter (category, within talent radius, no busy-block overlap), then the deterministic ranking from Search.

**Quality and operations**

- An eval set of 40–50 prompts with expected categories and missing fields runs in CI; accuracy is reported per release.
- Every call logs model, tokens, cost, latency and outcome (accepted, edited, discarded).
- Timeout of 10 s with a fallback to the manual posting form. Identical prompts within 24 h are served from cache.
- Prompts and schema are versioned; a schema change requires the eval to pass.

**Later:** embedding-based talent ranking (Phase 3); AI-suggested budget ranges from past gigs (backlog); AI portfolio tagging (backlog).

## Matching and notifications

When a gig is published, every talent in its category whose service radius covers the gig location and who is not busy at the gig time is notified exactly once.

**Matching rule:** `category ∈ talent.categories` AND (`distance(gig, talent.base) ≤ talent.radius_km` OR `talent.willing_to_travel`) AND no busy block overlaps the gig window. The talent's radius governs, not the poster's.

**Pipeline (Phase 2)**

1. Gig published → row written to an outbox table in the same transaction.
2. Outbox relay (polling, or Debezium CDC) publishes `GigPublished` to Kafka.
3. Matching worker runs the matching query in pages and emits one `NotifyTalent` event per recipient, keyed by talent id.
4. Channel workers deliver via in-app and email first; push and WhatsApp later.

**Rules**

- Idempotency key = (gig id, talent id, channel). A redelivered event never produces a second message.
- No daily cap: every matching gig notifies the talent, so no opportunity is missed. The notification badge shows a count (e.g. 9+), and the talent dashboard lists every match.
- Talent can mute categories or set quiet hours.
- Failed deliveries retry with backoff, then move to a dead-letter topic with an admin view.
- A talent who clears a busy block after publish is **not** retro-notified in Phase 2; they will see the gig in search. Revisit in Phase 3.

**Gig updates and cancellations** notify applicants, not the original broadcast list.

**Talent dashboard:** a home view of every open gig that matches the talent's categories, radius and availability, newest first, with no cap. Each entry shows category, schedule, distance, budget range and whether they have applied. It is the full record behind the notifications, so a talent who missed an alert still sees the gig.

## Applications, selection and booking

A poster selects exactly one applicant per gig, and the system guarantees that a talent cannot hold two overlapping bookings.

**Requirements (Phase 2)**

- Talent apply with a short note and an optional quote. One application per talent per gig. Posters see each applicant's quote before selecting.
- Posters can also invite a recommended talent directly; the invite becomes an application when accepted.
- Selecting an applicant creates a booking, a busy block for the talent, and moves the gig to Filled. Other applicants are notified.
- Talent must confirm a selection within 12 hours, or the booking lapses and the gig returns to Open.
- **Double-booking guarantee:** enforced in the database (e.g. a Postgres exclusion constraint on talent id + time range), not only in application code. A test fires concurrent selections of one talent for overlapping gigs and asserts exactly one succeeds.
- Selection is idempotent: retrying the same request returns the same booking.

**Messaging:** basic poster–applicant chat after an application exists (Phase 4). Until then, contact details are revealed only after selection.

## Payments and escrow

Posters pay into escrow on confirmation, and the platform releases funds to talent after the gig is marked complete (Phase 4, test mode).

- Payment gateway in test mode (e.g. Razorpay); UPI and cards.
- Funds held until the poster marks complete, or auto-released 72 hours after the gig ends if no dispute is raised.
- **Double-entry ledger** table: every movement (charge, hold, release, refund, fee) is a balanced pair of entries. Balances are derived, never stored as a mutable number.
- Gateway webhooks are processed idempotently using the gateway's event id; out-of-order webhooks are handled.
- Platform fee: a configurable percentage, shown before payment.
- Refunds follow the cancellation policy in the next section.

Real money movement, KYC and payouts to bank accounts are out of scope until GigWork is run as a business.

## Reviews, reliability and cancellation

Reliability (showing up as booked) is tracked separately from quality (star rating), because a no-show costs a poster more than an average shoot.

**Reviews:** both sides rate each other 1–5 with an optional comment, only after a completed booking. Reviews are hidden until both submit or 7 days pass, to avoid retaliation.

**Reliability score:** completed bookings divided by confirmed bookings, with late cancellations and no-shows counted against it. Shown as a badge after 3 completed gigs.

**Cancellation policy (draft)**

| Who cancels | When | Effect |
| --- | --- | --- |
| Poster | > 72 h before | Full refund |
| Poster | ≤ 72 h before | 50% to talent |
| Talent | > 72 h before | No penalty beyond reliability count |
| Talent | ≤ 72 h before or no-show | Full refund to poster; reliability hit; repeat offenders suspended |

The percentages and windows are placeholders to validate with real talent before Phase 4.

## Trust, safety and privacy

Many gigs in the focus categories mean a talent travelling to a stranger's home, so location privacy and poster accountability are product requirements, not extras.

- Talent exact location is never exposed; search shows locality and rounded distance.
- Gig exact address is revealed to the selected talent only, after confirmation.
- Phone numbers are hidden until a booking is confirmed.
- Posters must verify a phone number (OTP) before publishing; verified-identity badge for both sides in Phase 4.
- Report and block on profiles, gigs and messages, with an admin moderation queue.
- Portfolio uploads are scanned for file type and size; profanity and contact-detail filtering on gig text to keep deals on-platform.
- Data retention: deleted accounts are anonymised within 30 days; the ledger keeps anonymised entries.
- Compliance with India's Digital Personal Data Protection Act to be reviewed before any real launch.

## Non-functional requirements

| Area | Requirement |
| --- | --- |
| Architecture | Modular monolith (Spring Boot), modules: identity, profiles, gigs, matching, notifications, payments. Kafka for async work only. |
| Data | PostgreSQL + PostGIS as the system of record; Redis for caching and rate limits; S3 for media. |
| Performance | Talent search p99 < 100 ms at 50k profiles; API p99 < 300 ms excluding AI calls. |
| Reliability | No lost or duplicated notifications; outbox for every event that must follow a DB write. |
| Idempotency | Keys on selection, notification send, payment webhooks. |
| Observability | OpenTelemetry traces across API → Kafka → workers; metrics dashboard incl. consumer lag; structured logs with trace ids. |
| Security | JWT auth, role checks per endpoint, secrets in AWS Secrets Manager, rate limiting per IP and user. |
| Testing | Unit + Testcontainers integration tests in CI; AI eval suite; k6 load tests per phase. |
| Cost | Stay under roughly $30/month on AWS outside load-test days; no managed Kafka. |
| Documentation | ADR for every significant decision; README with architecture diagram, numbers and demo video. |

## Future features and improvements

Ranked by how likely they are to be built next once Phase 4 ships; the first group is near-certain if GigWork continues as a product.

**Likely next (after Phase 4)**

| Feature | Why | Size |
| --- | --- | --- |
| WhatsApp notifications and replies | Where the talent pool actually lives | M |
| Google Calendar sync for busy blocks | Keeps availability fresh without effort | M |
| Team booking for gig groups | Book a photographer + editor + reel creator as one package | L |
| Saved searches and alerts for posters | "Tell me when a mehendi artist near HSR frees up on 20 Jan" | S |
| Talent analytics | Profile views, notification-to-apply rate, earnings | S |

**Possible**

| Feature | Why | Size |
| --- | --- | --- |
| AI budget suggestion from past gigs | Posters rarely know market rates | M |
| AI portfolio tagging and style search | "Candid, pastel wedding photography" | L |
| Recurring gigs | Weekly dog walking, monthly content shoots | M |
| Agency accounts | One studio managing several talents | M |
| Seasonal demand forecasting | Wedding season spikes; nudge talent to update calendars | M |
| Native mobile apps | Push reliability, camera uploads | L |

**Unlikely unless the business case appears:** subscriptions for talent, ads, expansion beyond Bengaluru, real payouts and KYC.

## Risks and open questions

**Risks**

- **Scope versus job hunt:** interview prep competes for time from December. Mitigation: Phase 1 is fixed; Phases 2–4 may slip.
- **Stale availability:** if talent stop updating busy blocks, notifications become noise. Mitigation: busy-by-exception model, auto-blocks on booking, calendar sync in backlog.
- **AI miscategorisation:** a wrong category notifies the wrong people. Mitigation: enum-constrained output, human review, eval in CI.
- **Cold start:** no real users at launch. Mitigation: 50k seeded profiles for engineering work; real users only if the product track is pursued.
- **AWS cost creep** from Kafka, RDS and load tests. Mitigation: self-hosted Kafka, tear-down scripts, billing alarm.

**Open questions**

- [x] Final category taxonomy and whether categories have sub-skills (e.g. Photography → wedding, product, portrait).

  Decided: no sub-skills for now; keep the design open to add them.
- [x] Is a gig a single date-time window, or can it span multiple days?

  Decided: a single date-time, a window, or several days.
- [x] Should posters see applicant quotes before selecting, or only after?

  Decided: before selecting.
- [x] Notification cap per day: 5 is a guess; what do real talent want?

  Decided: no cap. Badge shows a count (9+); the dashboard lists every match.
- [x] Retro-notify talent who free up after a gig is published?

  Decided: later, not in the first release.
