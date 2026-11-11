# GigWork — Requirements

Status: Draft · Last updated 2026-11-11

This doc holds the agreed problem statement and functional requirements. Non-functional requirements, components and data come next, in that order.

## Problem statement

Creative and artistic talent and the people who want to hire them find each other through Instagram, other social media and word of mouth. There is no organised place where:

- **Discovery:** Posters can find suitable Talent beyond their own network.
- **Availability:** Posters can see who is free on their date.
- **Trust:** Posters can judge quality before hiring.
- **Timing:** Talent hear about relevant gigs as soon as they are posted.

**Focus categories (Phase 1, fixed list):** photographers, videographers, DJs, emcees, bands and live music, content creators, mehendi artists, event curators and organisers, painting, sketching and caricature artists, makeup artists, dance choreographers, video editors and content writers. The focus is artistic and talent-based work, mostly in person.

- **Content creators** are people a brand hires to *produce* content (shooting and editing reels for a café launch, for example). Influencers paid to post to their own audience are out of scope.
- **Groups** such as bands and orchestras have one account that represents the whole group.
- **Remote gigs** are supported. Each category is marked by the Admin as on-site, remote or both; video editing and content writing are the remote categories at launch. A remote gig reaches Talent in every live city in India.
- Home repairs and similar services are not a focus.

**Starting city:** Bengaluru, split into 5 broad zones. GigWork is built for many cities from day one: more tier 1 cities follow, then tier 2. Adding a city is an Admin action, not a code change.

## Actors

- **Visitor:** anyone not logged in.
- **Talent:** a user offering a service. A talent profile is an add-on to an account, not a separate account type.
- **Poster:** a user who publishes a gig. Any account can be a Poster, and one account can be both.
- **Admin:** the platform operator.
- **System:** timers and background jobs (reminders, expiry, notifications).

## Functional requirements

### Accounts and contact

- **FR-1** A visitor can sign up and log in using either their mobile number or their email address, with a one-time code (OTP). One account can both post gigs and register as Talent.
- **FR-2** Each user chooses one or more contact methods they are willing to share (WhatsApp, phone call, email).
- **FR-3** Every registered user verifies a mobile number by OTP when signing up, whether they act as Poster, Talent or both. An email address is verified later, by OTP while the user is logged in, the first time it is needed. The system records whether each mobile number and email address is verified. Login by email OTP and email notifications work only for a verified email address.
- **FR-3a** A user can delete their own account. Their personal data is erased within 30 days; gigs and applications they took part in are kept in anonymised form.

### Talent profile

- **FR-4** A user can register as Talent by choosing one or more categories from a fixed, pre-decided list. A Talent can hold up to 5 categories (a configurable limit). In Phase 1 there is no way to add categories from the app.
- **FR-5** Talent set a home location (by locality, PIN code or map pin; stored as coordinates, never as a street address), a service radius in km, and a willing-to-travel flag.
- **FR-6** Talent add portfolio links (website, Instagram and others).
- **FR-7** Talent add and remove busy blocks (date-time ranges when they are unavailable).
- **FR-8** Talent's exact location is stored but never shown to other users; only locality and city are shown. Search results also show a rounded distance, e.g. "\~4 km".

### Browse and search

- **FR-9** Anyone, logged in or not, can browse open gigs and Talent profiles. Visitors who are not logged in never see anyone's contact details.
- **FR-10** Browsing is filtered by one or more city zones. For a logged-in user, the default is their own zone. For a visitor, the city is detected from their IP address and all zones in that city are shown; they can then narrow to specific zones. Remote gigs appear regardless of the zones selected.
- **FR-11** Results can be filtered by category and date; gig results also by budget.
- **FR-12** Applying to a gig or seeing contact details requires an account.

### Gig creation

- **FR-13** A Poster can describe a need in natural language; the AI splits it into one draft gig per category. The AI picks only from the live category list and does not recommend Talent.
- **FR-14** If the AI cannot determine the category, date window or location, the Poster is asked; the AI never guesses these.
- **FR-15** A Poster can create a gig with a form, which is also the fallback when the AI is unavailable.
- **FR-16** A gig has a category, title, description, date window, an optional budget, and a work mode: on-site or remote (limited to what the category allows). An on-site gig also has a location (chosen the same way; no street address is collected) and a yes/no flag for whether travel allowance (TA) is offered.
- **FR-17** Drafts can be reviewed, edited or removed, then published together. Unpublished drafts are visible only to the Poster.
- **FR-18** A Poster can edit an open gig; applicants are notified of any change to date, time or location.
- **FR-19** A Poster can cancel an open gig. The gig shows as Cancelled on applicants' dashboards; no notification is sent, except to shortlisted Talent, who are told the gig was closed because they may be holding the date. Notifications are kept for positive or actionable news only.

### Matching and notifications

- **FR-20** When a gig is published, every Talent is notified who matches its category, has no busy block overlapping the gig's date window, and either has the gig within their service radius or is willing to travel to a gig that offers TA. For a remote gig, location is ignored: every Talent in the category, in any live city, who has no overlapping busy block is notified.
- **FR-21** A Talent is notified at most once per gig, even if the system retries.
- **FR-22** Talent have a dashboard listing open gigs that match them and gigs they have applied to. Users who are not Talent see an option to register as Talent instead.

### Applications, shortlisting and hiring

- **FR-23** Talent can apply to an open gig with an optional note, once per gig, and can withdraw the application.
- **FR-24** A Poster sees each application with the applicant's profile and portfolio links.
- **FR-25** A Poster can shortlist one or more applicants and, when shortlisting, chooses yes or no to sharing their own contact details with that Talent. Shortlisted Talent are notified.
- **FR-25a** A Poster can remove a Talent from the shortlist. From that moment the Poster and that Talent can no longer see each other's contact details in the app (details already saved outside the app can't be recalled). No notification is sent; the Talent's dashboard shows the application as not shortlisted.
- **FR-26** Contact details are revealed only with consent, for that gig only. A shortlisted Talent sees the Poster's chosen contact methods if the Poster chose to share them. Talent have a Shortlisted tab where they can accept to reveal their own contact methods to that Poster; the Poster sees them only after the Talent accepts.
- **FR-27** A Poster can mark one shortlisted Talent as hired. The gig becomes Filled, the hired Talent gets a busy block for the gig's date window, and the gig shows as Closed on the other applicants' dashboards. They are not sent a notification.
- **FR-28** A shortlisted Talent can mark "I've been hired for this". The Poster is asked to confirm with one tap; on confirmation, FR-27 applies.
- **FR-29** The system prevents a Talent from holding two overlapping hires recorded in the app. Hires agreed offline and never recorded in the app are not covered.

### Closing gigs

- **FR-30** The Poster is reminded to update the gig's status. Reminders go out 48 hours after the first shortlisting and one day before the gig starts.
- **FR-31** A gig with no hire closes automatically as Expired when its start time passes.
- **FR-32** When a gig is closed, the Poster is asked the outcome: hired on GigWork, hired elsewhere, or not hired.

### Admin

- **FR-33** An Admin can add, rename and retire categories.
- **FR-34** An Admin can add a city, define its zones, and switch the city live. Only live cities appear in browsing, search and sign-up.
- **FR-35** Users can report a gig or profile, and an Admin can hide it.

* **FR-36** An Admin can turn features on or off without a release, globally or per city (later also per Talent tier). Every flag has a safe default, and each change is recorded in the audit log.

## Non-functional requirements

NFRs are written for the whole app and tagged with the phase from which each applies. Every NFR states how it is measured. Design targets are about 10× the realistic estimate, so load tests run at the design target.

### Scale

| ID | Requirement | From phase | Measured by |
| --- | --- | --- | --- |
| NFR-1 | 50,000 Talent profiles per city; 250,000 across 5 cities | 1 | Seeded dataset; all latency targets are measured at this size |
| NFR-2 | 1,500 gigs published per day per city at peak (wedding season) | 1 | Load test |
| NFR-3 | On-site fan-out: about 4,000 recipients per gig typically, 9,000 worst case | 2 | Load test |
| NFR-4 | Remote fan-out: up to 30,000 recipients for one gig | 2 | Load test |
| NFR-5 | 3 million notifications per day per city at peak | 2 | Load test |
| NFR-6 | 300 concurrent users and 100 API requests per second per city (500 across 5 cities) | 1 | Load test |

**How these were estimated.** Bengaluru has about 1.4 crore people, around 1% of India, and India has roughly 1 crore weddings a year (CAIT estimates about 46 lakh in the Nov–Dec 2025 season alone), so about 90,000 weddings a year involve Bengaluru families. Each wedding hires about 4.5 of the focus skills, and non-wedding events roughly double that, giving about 8 lakh gigs a year in the city's market. Dividing demand by how many jobs one practitioner handles a year gives about 44,000 practitioners across the 13 categories. GigWork is assumed to reach 10–20% of practitioners and 1–2% of gigs within 2–3 years; design targets are 10× that. Fan-out uses geometry: a 10 km radius covers about 40% of Bengaluru's roughly 741 km², and a remote gig reaches a whole category across five cities.

**What the numbers show:** API load is modest (about 100 requests a second per city). The hard problem is burst fan-out: a single remote gig can produce about 30,000 notifications within seconds.

### Performance

All latencies are server-side, measured as percentiles at the design scale (NFR-1, NFR-6). "Queued" means handed to the delivery system; delivery time inside email or WhatsApp providers is not counted.

| ID | Requirement | From phase | Measured by |
| --- | --- | --- | --- |
| NFR-7 | Simple reads (profile, gig page, dashboard): p95 < 200 ms, p99 < 500 ms | 1 | Load test at NFR-6 traffic |
| NFR-8 | Writes (apply, shortlist, hire): p95 < 300 ms | 1 | Load test |
| NFR-9 | Search with category, date and distance filters: p95 < 300 ms at 50,000 profiles | 3 | Load test |
| NFR-10 | Gig publish to first notification queued: < 5 s | 2 | Trace from publish to first queued message |
| NFR-11 | Gig publish to last notification queued, on-site worst case (9,000 recipients): < 15 s | 2 | Load test; about 600 notifications/s |
| NFR-12 | Gig publish to last notification queued, remote worst case (30,000 recipients): < 60 s | 2 | Load test; about 500 notifications/s |
| NFR-13 | AI gig drafting: p95 < 8 s; hard timeout 10 s, then fall back to the form (FR-15) | 4 | Latency logged per AI call |

**Basis:** reads and writes come from a latency budget (a few indexed queries take tens of milliseconds; p95 allows 3–10× for tail effects) within the \~1 s a page can take before it feels slow on mobile. Notification targets come from the timing pain (Talent must hear within seconds) and are converted into throughput. The AI timeout sits at the \~10 s limit of user attention. See *How to estimate NFRs* for the method. All targets are revised after the first load test.

### Consistency

Strong consistency is required only where stale data causes real-world or privacy harm. Everywhere else, reads may be slightly stale within a stated bound, and every write is validated against the source of truth. Users always see their own changes immediately (read-your-writes).

| ID | Requirement | From phase | Measured by |
| --- | --- | --- | --- |
| NFR-14 | Hires and busy blocks are strongly consistent: two overlapping hires for one Talent can never both succeed, even under concurrent requests | 1 | Concurrency test: parallel hire attempts for the same Talent and time; exactly one succeeds |
| NFR-15 | Shortlist and contact visibility are strongly consistent: contact details are visible only while a Talent is shortlisted, and access ends immediately on removal (FR-25a) | 1 | Integration test: remove from shortlist, then the next read is denied |
| NFR-16 | At most one application per Talent per gig, enforced under concurrent requests | 1 | Concurrency test: parallel apply attempts; exactly one succeeds |
| NFR-17 | Gig status may be read stale, but every write (apply, shortlist, hire) is validated against the current status; writes to a closed gig are rejected with a clear message | 1 | Integration test: apply to a just-filled gig is rejected |
| NFR-18 | The fan-out worker's copy of Talent data (categories, location, radius, busy blocks) is at most 5 s behind the source | 2 | Lag metric from change event to applied copy |
| NFR-19 | Talent dashboards show new matching gigs within 5 s; profile edits are visible to others within 5 s and to their author immediately | 2 | End-to-end test with timestamps |
| NFR-20 | Search and browse results reflect new or changed gigs and profiles within 60 s | 3 | End-to-end test with timestamps |
| NFR-21 | Category, city and zone changes by an Admin take effect within 5 minutes | 1 | End-to-end test |

### Reliability

The app's own records are the source of truth and are never silently lost; delivery to outside providers (email, push) is best-effort, with the in-app record and the Talent dashboard as the fallback.

| ID | Requirement | From phase | Measured by |
| --- | --- | --- | --- |
| NFR-22 | Every gig that becomes Open starts its notification run; no published gig is silently skipped, even if a process crashes mid-publish (transactional outbox) | 2 | Fault test: kill the app between saving the gig and sending the event; notifications still go out |
| NFR-23 | In-app notification records are effectively-once per (gig, Talent, channel): never lost and never duplicated, including after retries and crashes | 2 | Fault test: crash and replay; exactly one record per recipient |
| NFR-24 | Email and push are sent at most once per notification, with no retries; failures are logged | 2 | Provider failure test: no retries, failure logged, in-app record present |
| NFR-25 | A fan-out run that crashes is restarted from the beginning; recipients already notified are not notified again (NFR-23) | 2 | Fault test mid-fan-out |
| NFR-26 | Every scheduled action (gig expiry, reminders) runs within 2 minutes of its due time, survives restarts, runs on exactly one instance, and has no extra effect if it runs twice | 1 | Restart and multi-instance tests |
| NFR-27 | Database backups with point-in-time recovery: RPO ≤ 5 minutes, RTO ≤ 4 hours, 7-day retention; a restore is tested once per phase. A standby replica in a second zone is added before real users arrive | 1 | Restore drill each phase |
| NFR-28 | OTP can be resent after 30 seconds, at most 3 times per 15 minutes per phone number | 1 | Integration test |

### Availability

The app can be no more available than its least available essential part. With a single-zone database (NFR-27), the realistic ceiling is about 99.5%.

| ID | Requirement | From phase | Measured by |
| --- | --- | --- | --- |
| NFR-29 | While an environment is running (and permanently after launch), monthly availability of 99.5% (about 3.6 hours of downtime a month), rising to 99.9% (about 44 minutes) once the standby database replica is added before real users | 1 | External health check every minute |
| NFR-30 | Core flows (post by form, apply, shortlist, hire) keep working when the AI provider, email or push provider, Kafka, the fan-out worker or search is unavailable. Notifications are delayed, not lost (the outbox holds them); search falls back to the plain gig list; AI drafting falls back to the form | 1 | Failure tests: stop each dependency in turn |
| NFR-31 | If an SMS OTP can't be delivered, a user with a verified email address can log in with an OTP sent by email instead. Sessions use a 15-minute access token, a 7-day idle timeout and a 30-day absolute limit, and changing a mobile number or email address always requires a fresh OTP. Because sessions are long-lived, an SMS outage affects only new logins | 1 | Failure test with SMS disabled |
| NFR-32 | Releases cause no downtime: rolling deploys across at least two app instances, with no failed requests during a deploy | 1 | Deploy while a load test is running |

### Security and privacy: who can see what

Least privilege: each person sees the minimum they need for their next step. Every rule is enforced by the server on every request, not just hidden in the app.

| Data | Who can see it |
| --- | --- |
| Talent's public profile (name, categories, locality, portfolio links) | Everyone, including visitors |
| Talent's exact location | No one; used only internally for matching |
| Talent's contact details | A Poster who shortlisted them, only after the Talent accepts to reveal, and while they remain shortlisted |
| Talent's busy blocks | Only the Talent |
| Gig details (title, category, dates, budget, locality) | Everyone, including visitors |
| Gig's exact location | No one; used only internally for matching. Addresses are shared directly between Poster and Talent |
| Poster's identity | Logged-in users see the first name; visitors see only the locality |
| Poster's contact details | Talent shortlisted for that gig, if the Poster chose to share them |
| Applicants for a gig | Only that gig's Poster |
| Number of applicants | Hidden from Talent (Phase 1) |
| An application's note | That gig's Poster and the applicant |

| ID | Requirement | From phase | Measured by |
| --- | --- | --- | --- |
| NFR-33 | Every API request is authorised on the server by ownership of the resource; IDs are random (UUIDs), so they can't be guessed in sequence | 1 | Automated tests that request other users' resources and expect a refusal |
| NFR-34 | Data visibility follows the table above | 1 | Tests per row, for each viewer type |
| NFR-35 | No street addresses are collected. Locations are stored as coordinates, and no API ever returns exact coordinates; only locality and a rounded distance are shown | 1 | API response checks |
| NFR-36 | Admins cannot view contact details or exact locations. Every Admin action is recorded in an audit log (who, what, when) | 3 | Tests on Admin APIs; audit log entries |

### Security and privacy: abuse and rate limits

A determined bad actor can't be stopped completely; the goal is to make abuse slow, expensive and low-yield. The biggest risk specific to GigWork is contact harvesting (posting fake gigs to collect Talent phone numbers), which is why contacts are revealed only with consent (FR-26).

| ID | Requirement | From phase | Measured by |
| --- | --- | --- | --- |
| NFR-37 | OTPs are sent only to Indian (+91) mobile numbers in Phase 1, with a per-IP limit on OTP requests. Codes expire after 5 minutes and allow at most 5 attempts | 1 | Integration tests |
| NFR-38 | A Poster can shortlist at most 10 Talent per gig and 20 per day. Accounts younger than 7 days can have at most 3 open gigs | 1 | Integration tests |
| NFR-39 | A Talent can submit at most 30 applications per day | 1 | Integration test |
| NFR-40 | Request rate limits: visitors 60 per minute per IP address, logged-in users 120 per minute; requests over the limit get "429 Too Many Requests". Browse results are always paginated | 1 | Load test |
| NFR-41 | Accounts showing harvesting patterns (many shortlists across gigs with no hires, or repeated "not a real gig" reports) are suspended automatically pending Admin review | 3 | Simulated abuse test |

### Security and privacy: protecting stored data

India's Digital Personal Data Protection Rules, 2025 were notified on 13 November 2025; the main obligations (notice and consent, security safeguards, breach reporting, erasure) apply from 13 May 2027. GigWork is designed to meet them from the start.

| ID | Requirement | From phase | Measured by |
| --- | --- | --- | --- |
| NFR-42 | All traffic is encrypted in transit (HTTPS/TLS), including the app's connections to the database and other internal services | 1 | Configuration check; plain connections refused |
| NFR-43 | All stored data is encrypted at rest: database, backups and file storage | 1 | Configuration check |
| NFR-44 | Mobile numbers and email addresses are encrypted by the application before they are stored. A keyed hash of each is stored separately so login can look them up; encryption keys are held in a key management service, not in the database | 1 | Inspect raw database rows: no readable contact details |
| NFR-45 | Secrets (database credentials, API keys) live in a secrets manager and never in the repository | 1 | Secret scanning in CI |
| NFR-46 | Logs never contain mobile numbers, email addresses, OTP codes or tokens; such values are masked | 1 | Automated log scan in tests |
| NFR-47 | Retention: OTP codes are deleted after use or expiry; notifications are kept 90 days; the Admin audit log is kept 1 year; a deleted account's personal data is erased within 30 days (FR-3a); backups expire after 7 days (NFR-27) | 1 | Scheduled cleanup jobs and their tests |
| NFR-48 | A plain-language privacy notice is shown at sign-up. A written breach runbook (who is notified, and within what time, including the 72-hour report to the Data Protection Board) exists by Phase 3 | 1 (notice), 3 (runbook) | Review |

### Operability: feature flags

| ID | Requirement | From phase | Measured by |
| --- | --- | --- | --- |
| NFR-49 | A feature flag change takes effect within 1 minute across all instances, with no deploy or restart | 1 | Toggle a flag under load; measure time until every instance applies it |
| NFR-50 | If flag settings can't be loaded, every flag falls back to its safe default (new or paid features off, core flows on); a flag failure never takes the app down | 1 | Failure test with the flag store unavailable |
| NFR-51 | Every flag change is recorded in the audit log (who, which flag, old and new value, when) | 1 | Audit log check |

### Cost and environments

Nothing runs in the cloud by default. The whole system is created from code when needed (a demo, an interview, a load test) and destroyed afterwards, so the monthly baseline is close to zero.

| ID | Requirement | From phase | Measured by |
| --- | --- | --- | --- |
| NFR-52 | When no environment is running, cloud cost stays under $2 a month (only things like infrastructure state and stored container images persist) | 1 | Monthly bill |
| NFR-53 | One pipeline run creates the full cloud environment from scratch, deploys the current version and loads seed data at NFR-1 scale, ready to use within 30 minutes | 1 | Timed pipeline run |
| NFR-54 | One pipeline run destroys the environment completely, leaving nothing that costs money. Every environment is destroyed automatically after 6 hours even if it was forgotten | 1 | Cost check after teardown; auto-destroy test |
| NFR-55 | A demo session costs at most $5 and a full load test at NFR-1 scale at most $10 | 1 (demo), 2 (load test) | Cost tags per session |
| NFR-56 | Cost guardrails: budget alerts at 50%, 80% and 100%; monthly hard caps for SMS and, from Phase 4, AI usage; every resource tagged by environment and component | 1 | Alert test; tag check |
| NFR-57 | The whole system also runs on a laptop with one command (Docker Compose) with seed data, as a free fallback for demos | 1 | Fresh-machine run |

### Observability

Observability is what makes every "measured by" column possible. Thresholds below are initial assumptions, to be revisited once the system is built and measured.

| ID | Requirement | From phase | Measured by |
| --- | --- | --- | --- |
| NFR-58 | Logs are structured (JSON) and every line carries a trace ID, so one request or gig publish can be followed across the core service, Kafka and the fan-out worker. Personal data is masked (NFR-46) | 1 | Follow one gig publish end to end by trace ID |
| NFR-59 | Metrics cover request rate, errors and duration per endpoint; database and runtime health; outbox oldest-unsent age, Kafka consumer lag, fan-out duration and notifications per second; and overdue scheduled actions | 1 (core), 2 (fan-out) | Dashboard review |
| NFR-60 | Distributed tracing with OpenTelemetry: every gig publish is traced end to end; ordinary reads are sampled at 10% during load tests | 2 | Trace present for every publish in a test run |
| NFR-61 | Dashboards exist for each NFR group (performance, fan-out, reliability) | 2 | Review |
| NFR-62 | Alerts by email on NFR breaches: p95 latency above target, error rate above 1%, outbox backlog older than 30 s, consumer lag growing for 2 minutes, a scheduled action overdue by more than 2 minutes | 2 | Trigger each alert in a test |
| NFR-63 | Business events are recorded from Phase 1: gig posted, application submitted, shortlisted, hire outcome (FR-32), so applications per gig, fill rate and time to first application can be measured | 1 | Event counts match test actions |
| NFR-64 | Before an environment is destroyed, load-test results and dashboard snapshots are saved to the repository | 2 | Results present in the repo after each run |

### Testing and maintainability

Much of the code is written by an AI coding agent and reviewed by a human, so automated tests and CI are the main guardrail for safe changes.

| ID | Requirement | From phase | Measured by |
| --- | --- | --- | --- |
| NFR-65 | Tests at every level: unit tests for domain logic; integration tests against real Postgres and Kafka (Testcontainers); contract tests checking the API against the OpenAPI spec and both the Java and Go fan-out workers against the same event schemas; a few end-to-end tests of the core loop; load tests (k6); and the fault tests named in these NFRs | 1 | Test suite in CI |
| NFR-66 | Every NFR with a test-based "measured by" has that test automated. Domain logic aims for about 80% line coverage, as a guide rather than a gate | 1 | Coverage report; NFR-to-test mapping |
| NFR-67 | CI runs on every pull request: build, tests, linting, secret scanning and dependency vulnerability scanning. Nothing merges to main unless CI passes; main is protected | 1 | Branch protection settings |
| NFR-68 | The CI run takes under 10 minutes; load tests run separately, on demand | 1 | CI timing |
| NFR-69 | Every change goes through a reviewed pull request, including changes made by the AI coding agent. Changes affecting architecture require an ADR first | 1 | Repository history |
| NFR-70 | Database migrations are versioned and backward compatible (expand, migrate, then contract), so old and new versions can run side by side during rolling deploys (NFR-32) | 1 | Migration test running the previous app version against the new schema |
| NFR-71 | The OpenAPI spec and event schemas live in the repository and are versioned; breaking changes require a new version | 1 | Contract tests |
| NFR-72 | The README gives one-command local setup and demo-pipeline instructions, and an ADR index is kept up to date | 1 | Fresh-machine setup |
| NFR-73 | Dependency updates arrive as automated pull requests and pass the same CI | 1 | Update PRs present |

## Phases

GigWork is built in vertical slices: Phase 1 delivers a thin but complete path from posting a gig to hiring someone, and each later phase improves that working path rather than adding a disconnected area.

| Phase | Theme | Requirements | Demo at the end |
| --- | --- | --- | --- |
| 1 | Core loop, form-based | FR-1, FR-2, FR-3, FR-4 to FR-8, FR-15, FR-16, FR-18, FR-19, a plain list of open gigs (basic FR-9), FR-23 to FR-27, FR-29, FR-31. Categories, cities and zones loaded as seed data. | One gig goes from posted to applied, shortlisted and hired |
| 2 | Matching and notifications | FR-20, FR-21, FR-22, FR-28, FR-30, FR-32 | Publishing a gig notifies matching Talent within seconds; Java vs Go fan-out benchmark |
| 3 | Browse and search | Full FR-9, FR-10, FR-11, FR-12, FR-33, FR-34, FR-35 | Anyone can find Talent and gigs by zone, category and date |
| 4 | AI gig creation | FR-13, FR-14, FR-17, then F-6 | One sentence becomes reviewed, published gigs |

## Monetisation readiness (parked)

Monetisation is off at launch, and gig payments stay offline with no commission. The aim is to build the hooks early so it can be switched on later without reworking tables or the fan-out worker. These items are recorded here to be discussed and decided one by one; none are agreed requirements yet.

- **Tiers and entitlements:** Talent tiers (Free, Basic, Pro, ProMax, monthly or yearly); tier limits as Admin-editable config; one entitlements check on the server for every request; all limits unlimited at launch, switchable per city via feature flags (FR-36).
- **Application cap per tier**, separate from the NFR-39 abuse limit, correct under concurrent applies.
- **Alert cap, daily digest and early access:** paid tiers notified first; the fan-out worker becomes tier-aware; NFR-10 to NFR-12 would need per-tier targets.
- **Referral credits:** bonus applications for both sides once the referee qualifies; an append-only credit ledger; abuse checks tied to NFR-41.
- **Gig boosts (Phase 3):** limited slots per zone, category and day; purchase as a saga; labelled "Promoted".
- **Promoted Talent (F-4 for ProMax)**, never overriding availability, category match or relevance.
- **Planner/business Poster plan:** higher limits; search Talent by "free on this date" without exposing busy blocks.
- **Payments scope moves earlier:** platform purchases (subscriptions, boosts) split from gig payments; idempotent webhooks, recurring billing, GST invoices, price versioning, refunds.
- **Forward a gig** when a Talent is booked that day, and **concierge posting** on behalf of planners.
- **Reviews moved earlier**, as a simple rating tied to the FR-32 outcome.
- **Profile analytics** for paid tiers.
- **Metrics events from day one** (gigs posted, applications per gig, fill rate, time to first application, referral and tier conversion).
- **Public SEO landing pages** per zone and category, and shareable links with attribution.
- **Demo environment** built with infrastructure as code: spun up with seeded data at NFR-1 scale, then torn down.

**Open decisions:** calendar month vs rolling 30 days for quotas; whether a withdrawn application returns its credit; what happens on downgrade when over a limit; order of using bonus vs tier credits; the notification delay for Free; boost refunds if a gig fills or is cancelled; who the Poster is for a forwarded gig; how concierge posting fits NFR-36; whether profile analytics reveal viewers' identities. Possible conflicts to resolve: FR-4's fixed limit of 5 categories, ADR-0001's payments service, and moving AI drafting earlier.

## Future scope

- **F-1** In-app chat between a Poster and shortlisted Talent.
- **F-2** In-app payments.
- **F-3** Reviews and ratings.
- **F-4** Promoted (paid) Talent, shown as clearly labelled recommendations alongside AI drafts.
- **F-5** An AI-assisted portfolio builder inside the app.
- **F-6 (Phase 2) Category proposals.** A Talent who can't find their category can propose one. The proposal is held as not-live: the Talent isn't matched or found under it yet. An AI evaluation checks it for duplicates and synonyms of existing categories, fit with the focus (artistic and talent-based work) and policy problems, and records pros and cons. A clear, non-duplicate proposal is created automatically, even if only one Talent proposed it, and becomes available to AI drafting straight away. Anything with a conflict or low confidence goes to an Admin for approval.

## Open questions

- Which cities come after Bengaluru, and on what criteria (a business decision; the design only needs to support many cities).

## Next

1. Non-functional requirements, with numbers.
2. Components.
3. Data: entities, access patterns and guarantees, then the datastore decision (SQL vs NoSQL) as an ADR.
4. Recheck ADR-0001 against the components.
