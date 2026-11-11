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

- **FR-1** A visitor can sign up and log in. One account can both post gigs and register as Talent.
- **FR-2** Each user chooses one or more contact methods they are willing to share (WhatsApp, phone call, email).
- **FR-3** Every registered user verifies a phone number by OTP when signing up, whether they act as Poster, Talent or both.

### Talent profile

- **FR-4** A user can register as Talent by choosing one or more categories from a fixed, pre-decided list. A Talent can hold up to 5 categories (a configurable limit). In Phase 1 there is no way to add categories from the app.
- **FR-5** Talent set a home location, a service radius in km, and a willing-to-travel flag.
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
- **FR-16** A gig has a category, title, description, date window, an optional budget, and a work mode: on-site or remote (limited to what the category allows). An on-site gig also has a location and a yes/no flag for whether travel allowance (TA) is offered.
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
- **FR-25** A Poster can shortlist one or more applicants; shortlisted Talent are notified.
- **FR-26** Once a Talent is shortlisted, the Poster and that Talent can see each other's chosen contact methods, for that gig only. Either side can reach out first.
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

## Phases

GigWork is built in vertical slices: Phase 1 delivers a thin but complete path from posting a gig to hiring someone, and each later phase improves that working path rather than adding a disconnected area.

| Phase | Theme | Requirements | Demo at the end |
| --- | --- | --- | --- |
| 1 | Core loop, form-based | FR-1, FR-2, FR-3, FR-4 to FR-8, FR-15, FR-16, FR-18, FR-19, a plain list of open gigs (basic FR-9), FR-23 to FR-27, FR-29, FR-31. Categories, cities and zones loaded as seed data. | One gig goes from posted to applied, shortlisted and hired |
| 2 | Matching and notifications | FR-20, FR-21, FR-22, FR-28, FR-30, FR-32 | Publishing a gig notifies matching Talent within seconds; Java vs Go fan-out benchmark |
| 3 | Browse and search | Full FR-9, FR-10, FR-11, FR-12, FR-33, FR-34, FR-35 | Anyone can find Talent and gigs by zone, category and date |
| 4 | AI gig creation | FR-13, FR-14, FR-17, then F-6 | One sentence becomes reviewed, published gigs |

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
