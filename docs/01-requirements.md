# Mini WSA — Requirements Document

> Status: Draft for review · Date: 2026-07-06 · Owner: Ricky
> Source: Akamai CSI "Mini Security Analytics Pipeline" take-home assignment
> Scope target: **Strong submission** (all required parts done well + 1–2 bonuses + docker-compose + tests + polished README)

This document is **stack-agnostic on purpose**. It describes *what* the system
must do and *what the environment must provide*, not *how* it will be built.
Technology choices (storage engine, messaging, etc.) are deferred to the SDD.

---

## 1. Problem Statement

### 1.1 The business problem
Security Operations Centers (SOCs) receive **millions of raw security events**
from an edge network. A single event ("this request to `/api/v1/login` was
blocked as a SQL injection") is noise. Value only emerges when events are
**classified, scored, and aggregated** so analysts can see *patterns* — which
attack types dominate, who the repeat offenders are, which paths are targeted —
and respond before damage is done.

Mini WSA is the **backend brain** of that war-room dashboard. It is not the UI;
it is the ingestion + enrichment + storage + analytics engine the UI queries.

### 1.2 Decomposition of the problem
The problem breaks into five cooperating concerns:

| # | Concern | What it solves |
|---|---------|----------------|
| P1 | **Ingestion & validation** | Reliably accept single or batched events, reject malformed ones with clear errors, and stamp a trusted server-side receive time. |
| P2 | **Enrichment** | Turn a raw event into an analyzable one: derive a human-readable attack type, compute a deterministic 0–100 threat score, and flag repeat-offender IPs using a sliding time window. |
| P3 | **Storage** | Persist enriched events so they can be queried efficiently by time range and by dimension (config, category, action, IP, path) at scale. |
| P4 | **Analytics / query** | Serve aggregated summaries and filtered, paginated raw samples — plus (optionally) time-series buckets and alert evaluation. |
| P5 | **Data generation** | Synthesize realistic events, including "attack waves," so the system can be exercised with meaningful data. |

### 1.3 The interesting/hard parts (where the real work is)
- **Deterministic threat scoring** with a **stateful component**: the
  "repeat offender" bonus requires counting events from the same client IP in a
  sliding 10-minute window at *ingest time* — a read-during-write that has real
  performance implications at scale.
- **Aggregation queries** (`byCategory`, `byAction`, top-N attackers/paths,
  average scores) over a time range — the shape of query that drives the
  storage choice.
- **Scale**: the assignment explicitly asks how the design handles 10×/100× load.

---

## 2. Environment Requirements

*What must be present to develop, build, run, and grade the system.* Concrete
versions are proposals to be locked in the SDD.

### 2.1 Mandated by the assignment
- **Language:** Java.
- **Framework:** Spring Boot (for the REST APIs).
- **Storage engine:** free choice — **decision deferred** (must be justified in README).
- **Version control:** public **GitHub** repository; small logical commits;
  milestone tags (`v0.1-ingestion`, `v0.2-enrichment`, `v0.3-stats`, …).
- **Testing:** unit tests for classification/enrichment logic + at least one
  API integration test.
- **Documentation:** README with build/run instructions, API docs (curl is
  fine), architecture description, storage justification, "what I'd improve,"
  and "what was challenging."

### 2.2 Developer toolchain (proposed)
- **JDK:** Java 21 (LTS).
- **Build tool:** Maven or Gradle (one, chosen in SDD).
- **Spring Boot:** 3.x.
- **Container runtime:** Docker + Docker Compose (bonus: full stack via
  `docker-compose up`; at minimum used to bring up storage/deps locally).
- **Test tooling:** JUnit 5, Spring Boot Test / MockMvc, and Testcontainers
  (recommended so integration tests run against real storage, not mocks).
- **HTTP client:** curl / HTTPie for manual API exercising.

### 2.3 Conditional (only if a given bonus is chosen)
- **Kafka + producer script** — only if the *Streaming Ingestion* bonus is in scope.
- **Rate-limiting mechanism** (in-process or backing store) — only if the
  *Rate Limiting* bonus is in scope.

### 2.4 Non-goals for the environment
- No production hosting / deployment infrastructure required.
- No authentication/authorization system required (not requested).
- No frontend/dashboard — this is a backend-only deliverable.

---

## 3. Software Requirements

### 3.1 Functional requirements

#### FR1 — Ingestion API  (Part 1)
- `POST /v1/events/ingest` accepts **a single event object or an array (batch)**.
- Validate each event against the schema: **required fields present**, **enum
  values valid** (`rule.category`, `rule.severity`, `action`), **timestamp is
  valid ISO-8601**.
- Return **201** on success, **400** on validation failure **with per-error details**.
- Assign a server-side **`receivedAt`** timestamp to each accepted event.
- (Design question for SDD: partial-batch behavior — all-or-nothing vs.
  per-item accept/reject report.)

#### FR2 — Classification & Enrichment  (Part 2)
On ingest, for each valid event:
1. **Classify** `rule.category` → `attackType` (fixed mapping):
   `INJECTION`→"SQL/Command Injection", `XSS`→"Cross-Site Scripting",
   `PROTOCOL_VIOLATION`→"Protocol Anomaly", `DATA_LEAKAGE`→"Data Exfiltration",
   `BOT`→"Bot Activity", `DOS`→"Denial of Service", `RATE_LIMIT`→"Rate Limiting".
2. **Compute `threatScore`** (integer, capped at 100):
   - severity: `CRITICAL`=40, `HIGH`=30, `MEDIUM`=20, `LOW`=10
   - action: `DENY`=+20, `ALERT`=+10, `MONITOR`=+0
   - path contains `/admin` or `/login`: +15
   - **> 5 events from same `clientIp` in the last 10 minutes**: +15 (repeat-offender)
3. **Store** original fields + `attackType` + `threatScore` + `receivedAt`.

#### FR3 — Statistics API  (Part 3)
- `GET /v1/stats/summary?configId=&from=&to=` returns aggregates for a config +
  time range: `totalEvents`, `byCategory` (count + `avgThreatScore`),
  `byAction` (counts), `topAttackers` (top 10 IPs by count, with avg score),
  `topTargetedPaths` (top 10 paths by count).
- `configId` optional → aggregate across all configs.

#### FR4 — Samples API  (Part 4)
- `GET /v1/events/samples` with optional filters `configId`, `from`, `to`,
  `category`, `action`, and pagination `limit` (default 20, max 100) + `offset`.
- Returns enriched records **sorted by `timestamp` descending**, plus a **total
  count** of matching events.

#### FR5 — Data Generator  (Part 5)
- Generates realistic random events, **simulates attack waves** (bursts from the
  same IP hitting the same path), configurable event count (e.g. 10,000),
  output feedable into the ingestion API.

#### Bonus (choose per effort budget — see Effort doc)
- **B1 Alerting:** `POST /v1/alerts/define` + `GET /v1/alerts/evaluate`
  ("N events of category X within Y minutes" → firing alerts).
- **B2 Streaming ingestion:** consume events from a **Kafka** topic + Docker
  Compose + producer script.
- **B3 Time-series:** `GET /v1/stats/timeseries?...&interval={1m|5m|1h}` →
  counts bucketed by interval.
- **B4 Rate limiting:** 429 on stats/samples when a client exceeds e.g. 100 req/min per IP.

### 3.2 Non-functional requirements

| ID | Requirement | Notes |
|----|-------------|-------|
| NFR1 | **Deterministic correctness** of classification & scoring | Same input → same output; heavily unit-tested (explicitly graded). |
| NFR2 | **Scalability** | Must run 10k+ events comfortably; design must articulate a credible 10×/100× path (interview topic). |
| NFR3 | **Input validation & error semantics** | Correct HTTP codes; structured 400s with details. |
| NFR4 | **Query performance** | Time-range + dimensional aggregations should be index-supported, not full scans, at target scale. |
| NFR5 | **Testability** | Clean seams so enrichment logic is unit-testable in isolation; ≥1 API integration test. |
| NFR6 | **Observability** | Basic structured logging of ingest/enrichment/query paths. |
| NFR7 | **Documentation & reproducibility** | A grader can build+run from README alone. |
| NFR8 | **Maintainability** | Clear module boundaries (ingest / enrich / store / query / generate). |

### 3.3 Explicit assumptions & open questions (to resolve in SDD)
- **Storage engine** is undecided (deliberately) — the SDD will present options
  with tradeoffs and pick one against NFR2/NFR4.
- Repeat-offender window semantics: counted against **already-ingested** events
  (not within the same batch) — to confirm.
- Batch failure semantics: all-or-nothing vs. partial report — to confirm.
- Timestamp basis for the window/scoring: event `timestamp` vs `receivedAt` — to confirm.
- **Stats `from`/`to` optionality:** the assignment only states `configId` is
  optional. Assumed: `from`/`to` are also optional and default to the full
  available range when omitted — to confirm.
- **Duplicate `eventId`:** assumed unique; de-duplication/idempotency is **not**
  required (listed as a possible "what I'd improve" item, not built now).
- **Full-fidelity storage:** all original DLR fields (`method`, `statusCode`,
  `userAgent`, `requestSize`, `responseSize`, nested `rule`/`geoLocation`, etc.)
  are persisted verbatim alongside the three enrichment fields — nothing dropped.
- No auth, no multi-tenancy isolation beyond `configId` filtering.

---

## 4. Acceptance / "Definition of Done" (Strong submission)
- All 5 required parts implemented and demonstrably working end-to-end via the data generator.
- 1–2 bonus challenges implemented (selection driven by effort budget).
- Unit tests covering the scoring matrix + edge cases; ≥1 integration test hitting real storage (Testcontainers).
- `docker-compose up` brings up app + storage (bonus target).
- README complete: build/run, curl API examples, architecture diagram, storage
  justification, improvements, challenges.
- Git history: incremental commits + milestone tags.
