# How to estimate NFRs

A reference for turning "the system should be fast and scale" into numbers you can defend, design against and test. Worked examples come from GigWork.

## The core idea

Non-functional requirements are never pulled from thin air. Every number comes from one of three sources, and good targets usually combine all three:

1. **The world**: how big the market is and how people behave (scale).
2. **The user**: what feels fast enough, and what the business can tolerate (latency, availability).
3. **The machine**: what the work actually costs on real hardware (latency budgets, throughput).

The recipe for any NFR: **estimate → add headroom → write how it's measured → measure → revise.** A target you can't measure isn't a requirement yet.

## 1. Scale: Fermi estimation

Break a number you can't know into smaller numbers you can look up or reasonably guess, then multiply.

1. **Find an anchor you can look up**: population, number of events (weddings), business listings (Justdial, Google Maps), published industry reports.
2. **Chain ratios** from the anchor to what you need, writing every assumption down.
3. **Cross-check from a second direction.** Supply-side and demand-side estimates should land in the same range.
4. **Apply a capture rate**: the share of the market your product realistically gets.
5. **Design for about 10×** the realistic number, and load-test at that.

**Supply from demand.** When nobody publishes how many practitioners exist:

> practitioners ≈ (events per year × share that hire the skill × people per event) ÷ jobs one practitioner does per year

**GigWork example (photographers in Bengaluru):** 90,000 weddings × 90% hire × 2 people = 162,000 person-jobs ÷ 40 weddings per photographer ≈ 4,000, × 2 for non-wedding work ≈ 8,000.

**Peaks matter more than averages.** Typical multipliers to apply:

- Daily peak hour ≈ 2–3× the daily average rate.
- Seasonal peaks (wedding season, sales, exams) ≈ 2–5× the yearly average.
- Always size for the peak of the peak.

**Geometry for location problems.** Share of a city covered by a radius r = πr² ÷ city area. GigWork: a 10 km radius covers about 314 of Bengaluru's \~741 km², about 40%.

## 2. Users and traffic

Turn user counts into request rates:

- **Daily active users (DAU)** = total users × share active per day (often 10–30% for a useful app).
- **Concurrent users** = DAU × average session length ÷ length of the active day × peak factor.
- **Requests per second** = concurrent users ÷ seconds between requests per user (often 10–30 s for a browsing app).

**GigWork example:** 17,000 DAU × 5 min ÷ 840 min × 3 ≈ 300 concurrent; 300 users ÷ \~10–20 s per request ≈ 15–30 req/s, designed for 100 req/s.

## 3. Latency: two directions, then reconcile

### Top-down: what the user needs

Human response-time thresholds (Jakob Nielsen):

| Response time | How it feels |
| --- | --- |
| \~100 ms | Instant |
| \~1 s | Noticeable, but flow isn't broken |
| \~10 s | Limit of attention; users leave or need progress feedback |

Subtract network time (mobile users in India to an AWS Mumbai server: roughly 30–100 ms each way) to get the server's share. For pages to feel fast, server time should be a few hundred milliseconds at most.

For background work, ask what the business needs: how fast must the result arrive to beat the alternative (a WhatsApp forward, a competitor)?

### Bottom-up: what the work costs

Break a request into steps and add typical costs. Orders of magnitude worth memorising:

| Operation | Typical cost |
| --- | --- |
| Main memory read | \~100 ns |
| SSD random read | \~0.1 ms |
| Round trip within one data centre | \~0.5 ms |
| Redis GET including network | \~0.5–1 ms |
| Postgres query by indexed key | \~1–5 ms |
| Geo query with a spatial index over \~50k rows | \~5–50 ms |
| Kafka publish with acknowledgement | \~2–10 ms |
| Mobile network round trip, India to a Mumbai region | \~30–100 ms |
| LLM call returning \~500 tokens of structured output | \~3–8 s |

**Tail latency:** p95 is commonly 3–10× the median because of garbage collection, cold caches, lock contention and shared hardware. Set percentile targets with that in mind; never set targets on averages.

**GigWork example (read request):** load balancer \~1 ms + app \~5 ms + two indexed queries \~10 ms + serialisation \~2 ms ≈ 20 ms median. A p95 target of 200 ms leaves room for the tail; missing it signals a design problem such as a missing index or an N+1 query.

### Reconcile

The target sits between the bottom-up estimate and the user's limit, usually 2–5× above the estimate.

## 4. Throughput: turn time targets into rates

> rate = items ÷ time allowed

**GigWork example:** 9,000 notifications in 15 s = 600/s; 30,000 in 60 s = 500/s.

Then judge the rate against the stack: is it **cheap**, **achievable with good design**, or **heroic**? Kafka handles tens of thousands of messages a second per partition, so the bottleneck is the per-item work (queries, idempotency records, writes). 500–600/s is achievable with batching but not with a naive row-by-row loop, which is a good target for a portfolio project because it forces real engineering.

**Little's law** sizes pools and queues:

> items in flight = throughput × time each item takes

At 100 requests/s × 0.2 s, about 20 requests are in flight, so thread pools and the database connection pool need room for at least that, plus headroom.

## 5. Availability: the nines

| Availability | Downtime per month | Downtime per year |
| --- | --- | --- |
| 99% | \~7.3 hours | \~3.65 days |
| 99.5% | \~3.65 hours | \~1.8 days |
| 99.9% | \~44 minutes | \~8.8 hours |
| 99.95% | \~22 minutes | \~4.4 hours |
| 99.99% | \~4.4 minutes | \~53 minutes |

- **Dependencies in series multiply:** two components at 99.9% each give about 99.8% together. Every extra hop lowers the total.
- **Redundancy in parallel adds nines:** two independent copies at 99% each give about 99.99%, if they truly fail independently.
- **Each extra nine costs roughly 10× more effort** (redundancy, automation, on-call). Choose per feature: "hire" may need more than "browse".
- **Error budget:** 99.9% means 44 minutes a month you're allowed to spend on failures and risky deploys.
- Also define **graceful degradation**: what still works when a dependency is down (for example, posting by form when the AI is unavailable).

## 6. Storage

> storage = rows × bytes per row × (1 + index overhead) × growth over the retention period

- Estimate bytes per row by adding up column sizes (IDs 8–16 bytes, timestamps 8, short text \~50–200, JSON as measured).
- Indexes often add 50–100% on top of the table.
- Add append-only data separately (notifications, events, logs): these usually dominate.

**GigWork example:** 3 million notifications a day × \~200 bytes ≈ 600 MB a day per city at peak, so notification history needs a retention policy (for example, 90 days) or it becomes the biggest table by far.

## 7. Cost

- List each component (compute, database, cache, message broker, storage, bandwidth, third-party APIs such as SMS, email and LLM tokens).
- Estimate each from usage: for example, SMS cost = OTPs per day × price per SMS; LLM cost = requests × tokens per request × price per token.
- Separate **always-on** costs (idle baseline) from **usage** costs (grow with traffic) and **test** costs (load-test days).
- Look up current prices when estimating; they change often.

## 8. Consistency and reliability

These are decisions rather than calculations. For each piece of data, ask:

- **What breaks if two users see different values for a moment?** If nothing (a dashboard, search results), eventual consistency within seconds is fine. If money moves or a person is double-booked, it must be strongly consistent.
- **What breaks if a message is lost? Duplicated?** Decide per message type: at-most-once, at-least-once with idempotent handling, or effectively-once.
- **How long can it take to recover?** RTO (time to restore service) and RPO (how much data you can afford to lose), for example RPO 5 minutes via backups.

## 9. Writing an NFR

Every NFR row should have:

| Field | Example |
| --- | --- |
| ID | NFR-11 |
| Requirement with a number | Gig publish to last notification queued, 9,000 recipients: < 15 s |
| Applies from | Phase 2 |
| Measured by | Load test; \~600 notifications/s |
| Basis | Where the number came from (one line) |

Checklist: a percentile, not an average; a defined load or data size; a measurement method; and the reasoning, so the number can be challenged step by step instead of as a whole.
