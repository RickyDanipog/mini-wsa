# Mini WSA — Software Design Document (SDD)

> Status: Draft for review · Date: 2026-07-06 · Owner: Ricky
> Companion to `01-requirements.md` and `02-effort-estimate.md`.
> Purpose: a **wide, high-level overview** of the system — components, data
> flow, key design decisions, and the **storage-engine tradeoff analysis** so
> the stack can be chosen from an informed position. Framework is fixed
> (Java + Spring Boot); the **storage engine is presented as an open decision**
> in §7.

---

## 1. Design Goals & Principles

- **Storage-agnostic core.** Domain logic (validation, classification, scoring,
  aggregation contracts) depends on an interface, not a concrete engine. The
  engine can be swapped without touching business rules → satisfies "decide the
  stack later" and keeps enrichment unit-testable (NFR5, NFR8).
- **Pure where possible, stateful where necessary.** The deterministic part of
  scoring is a pure function; the one stateful input (repeat-offender count) is
  injected, so the scoring rules can be tested with zero infrastructure.
- **Push work to the store.** Aggregations run *in* the storage engine
  (GROUP BY / top-N), not by pulling rows into app memory (NFR4).
- **Append-only event data.** Events are immutable once enriched — this shapes
  both the storage choice and the scaling story.
- **Clear module seams.** Ingest / Enrich / Store / Query / Generate are
  separate, independently understandable units.

---

## 2. System Context (C4 level 1)

```
        ┌─────────────────┐        POST /v1/events/ingest        ┌──────────────────┐
        │  Data Generator │ ───────────────────────────────────▶ │                  │
        │  (script/module)│        (single or batch)             │                  │
        └─────────────────┘                                      │    Mini WSA      │
                                                                 │  (Spring Boot)   │
        ┌─────────────────┐   GET /v1/stats/summary              │                  │
        │  Analyst / SOC  │   GET /v1/events/samples             │                  │
        │  dashboard,curl │ ◀──────────────────────────────────▶ │                  │
        └─────────────────┘   (+ bonus: timeseries, alerts)      └────────┬─────────┘
                                                                          │
                                                                 ┌────────▼─────────┐
                                                                 │  Storage Engine  │
                                                                 │   (see §7)       │
                                                                 └──────────────────┘
   (bonus B2) Kafka topic ──▶ Streaming consumer ──▶ same enrichment pipeline
```

Mini WSA is a single backend service. External actors: the **data generator**
(producer), **analysts/tools** (consumers), and the **storage engine**
(dependency). Optional Kafka path feeds the *same* enrichment pipeline.

---

## 3. Internal Architecture (C4 level 2/3)

Layered Spring Boot application:

```
┌──────────────────────────────────────────────────────────────────────────┐
│  WEB / API LAYER  (REST controllers, DTOs, bean validation, error mapper)  │
│   IngestController · StatsController · SamplesController                    │
│   [bonus] TimeseriesController · AlertsController · RateLimitFilter         │
├──────────────────────────────────────────────────────────────────────────┤
│  SERVICE / DOMAIN LAYER                                                     │
│                                                                            │
│   IngestionService ──▶ EnrichmentPipeline                                  │
│                         ├─ AttackTypeClassifier   (pure)                    │
│                         ├─ ThreatScoreCalculator  (pure, count injected)    │
│                         └─ RepeatOffenderChecker  (stateful, §6.2)          │
│                                                                            │
│   StatsService · SamplesService · [bonus] TimeseriesService/AlertService   │
├──────────────────────────────────────────────────────────────────────────┤
│  PERSISTENCE / PORT LAYER   (interfaces — the storage-agnostic seam)        │
│   EventRepository:  save(events) · countByIpWithin(ip, window)              │
│                     summarize(filter) · findSamples(filter,page)            │
│                     [bonus] bucketCounts(filter, interval)                  │
├──────────────────────────────────────────────────────────────────────────┤
│  ADAPTER LAYER   (one implementation per chosen engine — §7)                │
│   <Engine>EventRepository  +  schema/migrations  +  connection config       │
└──────────────────────────────────────────────────────────────────────────┘
```

**Key seam:** `EventRepository` is the port. Everything above it is pure Java
domain logic; everything below it is engine-specific. Choosing/changing the
storage engine = writing one adapter.

### Component responsibilities

| Component | Does | Depends on |
|-----------|------|------------|
| `IngestController` | Parse single/array body, invoke ingestion, map results to 201/400 | IngestionService |
| `IngestionService` | Orchestrate validate → enrich → persist for each event; assign `receivedAt`; define batch semantics | EnrichmentPipeline, EventRepository |
| `AttackTypeClassifier` | `rule.category` → `attackType` (fixed map) | — (pure) |
| `ThreatScoreCalculator` | Compute 0–100 score from severity/action/path + injected offender flag | — (pure) |
| `RepeatOffenderChecker` | Answer "> 5 events from this IP in last 10 min?" | EventRepository (or cache, §6.2) |
| `StatsService` | Build summary aggregates over filter | EventRepository |
| `SamplesService` | Filtered, sorted, paginated retrieval + total count | EventRepository |
| `EventRepository` (port) | Storage-agnostic persistence + query contract | storage adapter |
| `DataGenerator` | Emit realistic events + attack waves | ingest API / file |

---

## 4. Data Model

### 4.1 Enriched event (logical)
Original DLR fields **+** `attackType` (string), `threatScore` (int 0–100),
`receivedAt` (server timestamp). Nested `rule` and `geoLocation` retained.

### 4.2 Access patterns that drive the physical model
1. **Insert** (single + batch, high volume, append-only).
2. **Point-ish count**: events for one `clientIp` in a 10-min window (ingest hot path).
3. **Range aggregation**: filter by `configId` + `[from,to]`, then GROUP BY
   category / action, AVG(threatScore), top-N by IP and by path.
4. **Range scan + page**: filter (configId, time, category, action), sort by
   `timestamp` desc, limit/offset, total count.
5. **[bonus]** Range + time-bucket counts.

### 4.3 Indexing implications (engine-independent)
- Time is the primary dimension → **index/partition on `timestamp`** (and usually `configId`).
- Repeat-offender check → index on **`(clientIp, timestamp)`**.
- Samples/stats filters → composite indexes on `(configId, timestamp)`, plus
  `category` / `action` as secondary filters.

---

## 5. Data Flow

### 5.1 Ingestion + enrichment (write path)
```
Request (1 event or array)
  → deserialize + bean-validation (required fields, enums, ISO-8601 timestamp)
      ├─ invalid → collect error detail
  → stamp receivedAt
  → EnrichmentPipeline per event:
        classify attackType
        offenderFlag = RepeatOffenderChecker.isRepeat(clientIp, now)   ← stateful read
        threatScore  = ThreatScoreCalculator.score(severity, action, path, offenderFlag)
  → EventRepository.save(enrichedEvents)   (batched insert)
  → respond 201 (accepted) / 400 (validation details)   [batch semantics: §8]
```

### 5.2 Stats query (read path)
```
GET /v1/stats/summary?configId&from&to
  → validate params
  → StatsService → EventRepository.summarize(filter)
        (engine computes: total, byCategory{count,avg}, byAction{count},
         top10 IPs, top10 paths — all as pushed-down aggregations)
  → assemble response DTO → 200
```

### 5.3 Samples query (read path)
```
GET /v1/events/samples?filters&limit&offset
  → clamp limit (default 20, max 100)
  → SamplesService → EventRepository.findSamples(filter, page)
        (WHERE filters, ORDER BY timestamp DESC, LIMIT/OFFSET, + COUNT(*))
  → { total, limit, offset, results[] } → 200
```

---

## 6. Key Design Decisions

### 6.1 Threat scoring engine (deterministic core)
`ThreatScoreCalculator.score(severity, action, path, isRepeatOffender)` is a
**pure function** — no I/O — so the entire scoring matrix (severity tiers,
action bonuses, `/admin`|`/login` bump, repeat bonus, cap at 100) is exhaustively
unit-testable. The only non-pure input, the offender flag, is computed
*outside* and passed in. This isolation is deliberate: it's the graded logic and
the interview deep-dive topic.

### 6.2 Repeat-offender sliding window (the stateful part)
"> 5 events from same `clientIp` in last 10 min" requires a **read during the
write path** — the design-sensitive decision. Three strategies:

| Strategy | How | Pros | Cons |
|----------|-----|------|------|
| **A. Storage query** (baseline) | `countByIpWithin(ip, 10m)` against the store, backed by `(clientIp, timestamp)` index | Correct, consistent, simplest, survives restarts, works across instances | A read per event on the hot path; at high volume needs batching/grouping |
| **B. In-process sliding window** | Per-IP time-bucketed counter / Caffeine cache | Very fast, no round-trip | Process-local (wrong under horizontal scale), lost on restart, memory unbounded without eviction |
| **C. Shared cache (Redis)** | Sorted set per IP: `ZADD ts`, `ZCOUNT [now-10m, now]`, TTL | Fast + shared across instances + scales out | Extra dependency & failure mode |

**Decision:** this lives behind its own **`OffenderWindow` port**
(`countRecent(ip, window)`), separate from `EventStore`, so its backing can
change independently. Build target: **A via MongoDB** — an indexed
`countDocuments({clientIp, receivedAt >= now-10m})` on `{clientIp:1, receivedAt:1}`
(correct, simple, in-comfort-zone). **Scale step: C via Redis** (sorted-set
sliding window + TTL) — swapped in behind the same port with zero change to the
scorer or event store; also the interview "one thing I learned" item. **B** is
named only as the tempting-but-wrong-under-horizontal-scale middle. For batch
ingest, group by IP to collapse N lookups into one — the "performance
implications" answer.

### 6.3 Aggregations pushed to the engine
`byCategory`, `byAction`, top-N, and averages are computed by the storage engine
(SQL `GROUP BY … ORDER BY count DESC LIMIT 10`, or the engine's aggregation
API), never by materializing rows in the JVM. Keeps memory flat and latency
bounded as data grows (NFR4).

### 6.4 Storage-agnostic repository port
A single `EventRepository` interface is the contract; each candidate engine gets
one adapter. Benefits: the stack decision (§7) is reversible, tests can run
against an in-memory fake for unit tests and the real engine (Testcontainers)
for integration tests, and the domain never leaks storage concerns.

---

## 7. Storage Engine — Decision

> **DECIDED (2026-07-07):** Persistence is split into **two ports** — `EventStore`
> (save events, stats aggregation, sample retrieval) and `OffenderWindow`
> (`countRecent(ip, window)` for the repeat-offender rule). Progression:
>
> 1. **Now (done):** in-memory adapters behind both ports — runnable dry-run env.
> 2. **Build target:** **MongoDB** behind both ports (chosen for developer
>    fluency + document fit + capable aggregation pipeline).
> 3. **Scale step / learning item:** **Redis** behind `OffenderWindow` only
>    (sorted-set sliding window + TTL), and optionally hot-stat caching. Redis is
>    a *complement*, not the source of truth; treat its state as rebuildable.
> 4. **Comparative load test:** implement a **PostgreSQL** `EventStore` adapter
>    and benchmark it against Mongo — the final justification is data, not
>    opinion.
>
> Switching any of these is only ever a new outer-ring adapter; domain and
> application code never change. **ClickHouse is intentionally words-only** — a
> columnar-OLAP talking point for the "analytics at 100×" question, not code
> (unfamiliar tech is kept off the critical path). The table below is the input
> to the load test, not a commitment.

Event data is **append-only, timestamped, high-volume**, queried by **time range
+ dimensional aggregation**. That profile is the deciding factor.

| Option | Fit for this workload | Scale story | Cost to build here | Interview narrative |
|--------|----------------------|-------------|--------------------|---------------------|
| **PostgreSQL** (relational + JSONB) | Good: SQL GROUP BY/top-N easy, JSONB holds nested `rule`/`geo`, solid indexing & time partitioning | Time-partitioning + read replicas; migrate hot analytics to OLAP later | **Lowest** — familiar, one container | "Start pragmatic and correct; evolve to a columnar store when analytics volume demands it." |
| **ClickHouse** (columnar OLAP) | **Best**: built for exactly time-range + GROUP BY over billions of append-only rows; immutability fits perfectly | Native — this *is* the big-data answer; sharding + materialized rollups | Medium (+learning curve) | "Chose the tool the real WSA-class system would use for analytics at scale." |
| **Elasticsearch/OpenSearch** | Strong for the samples/filter API + aggregations; closest to real Akamai WSA | Sharding built-in | Medium; heavier footprint | "Log-analytics native; great search + aggregation." Must explain eventual consistency. |
| **In-memory (H2/maps)** | OK for demo only | **Weak** — the explicit "big data scale" requirement is unmet | Lowest | Only defensible as a stated MVP tradeoff. |

**Chosen build stack:** **MongoDB** (`EventStore`) + **Redis** (`OffenderWindow`
scale step / hot-stat cache), built on developer fluency so every line is
defensible in the interview. **PostgreSQL** is implemented as a second
`EventStore` adapter purely for a **comparative load test** — the relational
baseline that justifies (or challenges) the Mongo choice with numbers.
**ClickHouse** stays a words-only scaling narrative (columnar OLAP reads only the
columns it aggregates → ideal for time-range GROUP-BY at 100×), not a
dependency. Elasticsearch: noted, not pursued.

---

## 8. API Design & Error Handling

- **Envelope & codes:** 201 (ingest success), 400 (validation, with a
  `details[]` of per-field/per-item errors), 200 (queries), 404 (n/a here),
  429 (only if rate-limit bonus), 500 (unexpected).
- **Validation:** declarative bean validation on DTOs + enum binding; a
  `@ControllerAdvice` maps failures to structured 400 bodies.
- **Batch semantics (OPEN — see requirements §3.3):** proposed **partial
  accept** — insert valid events, return 201 with a per-item report of any
  rejected ones; *or* strict all-or-nothing 400. Recommend partial-accept with a
  summary, as it matches real ingestion pipelines. **To confirm.**
- **Pagination:** `limit` default 20 / max 100 (clamped), `offset`, `total` in
  every samples response.

---

## 9. Scalability — 10× / 100× (interview topic)

| Layer | 10× | 100× |
|-------|-----|------|
| **Ingestion** | Batch inserts; stateless app → scale horizontally behind LB | Decouple via **Kafka** (bonus B2): producers → topic → consumer pool; back-pressure & replay |
| **Repeat-offender** | Group-by-IP within batch; add `(clientIp,timestamp)` index | Move to **Redis** sliding window (strategy C) shared across instances |
| **Storage** | Time-partitioning + proper indexes (Postgres) | **Columnar OLAP (ClickHouse)** + sharding; **pre-aggregated rollups / materialized views** for stats; retention/TTL on raw |
| **Stats/query** | Index-supported aggregations | Serve summaries from rollup tables; cache hot ranges; time-series from pre-bucketed data |

Core enabler: events are **immutable** → trivially partitionable, cacheable, and
rollup-friendly.

---

## 10. Testing Strategy

- **Unit (graded):** `ThreatScoreCalculator` — full matrix (each severity, each
  action, path bump, offender bump, cap-at-100, combinations) as a pure-function
  table test; `AttackTypeClassifier` — every category + unknown handling;
  `RepeatOffenderChecker` — boundary at exactly 5 / 6 events, window edges.
- **Integration (≥1, graded):** ingest → store → query round-trip against the
  **real engine via Testcontainers**; validation-error paths (400 shapes);
  pagination + total-count correctness; stats aggregation correctness on a seeded
  dataset from the generator.
- **Fakes:** in-memory `EventRepository` for fast service-layer unit tests.

---

## 11. Cross-Cutting Concerns

- **Config:** engine connection, window size (10m), thresholds, page caps via
  `application.yml` (no magic numbers buried in code).
- **Logging:** structured single-line logs on ingest counts, enrichment
  outcomes, query latency.
- **Build/run:** `docker-compose up` → app + storage (+ Kafka/Redis if a bonus
  needs them); README with curl examples.
- **Milestones/tags:** `v0.1-ingestion` → `v0.2-enrichment` → `v0.3-stats` →
  `v0.4-samples` → `v0.5-generator` → bonuses, mirroring commit breakdown.

---

## 12. Bonus Integration Points (where each bolts on)

| Bonus | Bolts onto | Added surface |
|-------|-----------|---------------|
| **B3 Time-series** | StatsService / EventRepository | `bucketCounts(filter, interval)` + `TimeseriesController` |
| **B4 Rate limiting** | Web layer | `RateLimitFilter` (token bucket per IP) → 429 |
| **B1 Alerting** | new AlertService + store | `alert_rules` persistence, `define`/`evaluate` endpoints |
| **B2 Streaming** | IngestionService | Kafka consumer → *same* EnrichmentPipeline; compose + producer |

---

## 13. Open Decisions (blockers for finalizing)

1. ~~**Storage engine** (§7)~~ — **RESOLVED:** in-memory adapter first; real DBs
   (Postgres, Mongo, others) load-tested behind the same port in a later phase.
2. **Repeat-offender basis** — event `timestamp` vs `receivedAt`; count against
   already-persisted events only (recommended). *Still open.*
3. **Batch failure semantics** (§8) — partial-accept (recommended) vs all-or-nothing. *Still open.*
4. **Bonus selection** — recommended B3 + B4; B1 stretch; B2 last. *Still open.*

Storage is resolved; the remaining three are minor and settle at implementation start.
