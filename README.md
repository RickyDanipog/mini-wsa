# Mini WSA — Mini Security Analytics Pipeline

An event-driven backend that ingests security event logs (DLRs), classifies and
enriches them, computes a deterministic threat score, persists them, and exposes
analytics APIs over the result. It is the backend brain a SOC dashboard would
query — not the UI. Akamai CSI backend take-home.

The pipeline is built as **five small services connected by Kafka**, each
hexagonal internally (pure domain, ports, swappable adapters). Storage is chosen
per service at runtime via a single `wsa.storage` switch — the same business
logic runs unchanged over in-memory or PostgreSQL, with Redis for the offender window.

## Architecture

```
 event-generator ──REST──▶  gateway   ──events.raw──▶  enrichment  ──events.enriched──▶  event-store
   (Part 5, CLI)             :8081                       :8082                              :8083
                             validate                    classify + score                  persist
                             publish                     offender window                     │
                                                          (in-memory | Redis)                 ▼
                                                                                    shared store (PostgreSQL)
                                                                                              ▲
                                                                                        analytics :8084
                                                                                    /stats/summary, /events/samples
```

- **[gateway](services/gateway)** — validates single or batched events, stamps a correlation id, publishes `events.raw` (keyed by `clientIp`).
- **[enrichment](services/enrichment)** — classifies `rule.category` → `attackType`, computes `threatScore`, flags repeat-offender IPs via a sliding window, publishes `events.enriched` (keyed by `configId`).
- **[event-store](services/event-store)** — consumes `events.enriched` and persists the full record (sole writer, de-duplicated by `eventId`).
- **[analytics](services/analytics)** — reads the same store read-only and serves aggregate stats + filtered, paginated samples.
- **[event-generator](services/event-generator)** — a CLI that synthesizes realistic events and attack waves and feeds them to the gateway.
- **demo-ui** (`localhost:8090`) — the **Simulation Console**: a single-file dashboard (nginx-served, same-origin proxied) that exercises every use case live — ingest, attack waves with a configurable inter-wave interval, custom-event crafting with score read-back, scoring-rule CRUD, stats/samples/time-series, alerts, and the rate limiter.

Each service has its own README with entry points, configuration, and a
standalone run command.

The shared Kafka message API and the shared store schema live in
[`services/contracts`](services/contracts) and are the single source of truth
for cross-service shapes. Design detail: [`docs/03-sdd.md`](docs/03-sdd.md).

## Tech stack

Java 21 · Spring Boot 3.3 · Maven (multi-module reactor) · Apache Kafka (KRaft) ·
Redis · PostgreSQL · Docker Compose · JUnit 5 + Testcontainers.

## Build & run

Prerequisites: **JDK 21**, **Maven**, **Docker** (for Compose and the integration tests).

```bash
# build everything + run all tests (unit + Testcontainers integration)
mvn clean verify

# bring up the full pipeline (Kafka + Postgres + Redis + 4 services + demo console)
docker compose up --build

# tail one service
docker compose logs -f analytics
```

Once the stack is up, open the **Simulation Console at http://localhost:8090** — a
single-page dashboard that drives every use case (ingest, attack waves, custom-event
scoring, live rule editing, stats/samples/time-series, alerts, rate limiting) with no
curl. The services listen on `:8081`–`:8084`. Health/liveness:

```bash
curl -s localhost:8081/actuator/health   # gateway   (repeat for 8082/8083/8084)
curl -s localhost:8081/v1/ping           # {"status":"ok","service":"gateway"}
```

Run a single service locally without Docker (defaults to in-memory storage, no
external deps needed):

```bash
mvn -pl services/gateway spring-boot:run
```

## Claude Code skills

If you open this repo in [Claude Code](https://claude.com/claude-code), it ships
a set of skills (under [`.claude/skills/`](.claude/skills)) so you can drive the
whole project by name instead of remembering commands:

| Skill | What it does |
|-------|--------------|
| `/prepare` | Detect & install prerequisites (Docker, JDK 21, Maven) — macOS. |
| `/deploy` | Bring up the full stack **+ the Simulation Console**, wait for health, smoke-test. |
| `/walkthrough` | Exercise every required part + bonus end-to-end and print a pass/fail checklist. |
| `/generate` | Seed the running pipeline with realistic events and attack waves. |
| `/add-rule` | Add a scoring rule, then ingest a matching event and show the threat score rise — live, no restart. |
| `/reset` | Wipe all data volumes and bring the stack back up clean (optionally reseed). |
| `/profile-storage` | Benchmark the storage candidates and find the scaling knee. |

Typical first run: `/prepare` → `/deploy` → open `localhost:8090` (or `/walkthrough`
to verify headlessly). These are plain Markdown — nothing to install, and they're
ignored entirely if you don't use Claude Code.

## API reference

All endpoints are versioned under `/v1`. Request/response bodies are JSON.

### `POST /v1/events/ingest`  (gateway :8081)

Accepts **a single event object or an array** (batch). Validation is
**all-or-nothing**: if any event in the batch is invalid the whole request is
rejected. Every accepted event is stamped with a server-side `receivedAt`
downstream. An optional `x-correlation-id` header is propagated through the
pipeline (one is generated if absent).

```bash
curl -s -X POST localhost:8081/v1/events/ingest \
  -H 'content-type: application/json' \
  -H 'x-correlation-id: demo-001' \
  -d '[
    {
      "eventId": "evt-0001",
      "timestamp": "2026-07-07T09:00:00Z",
      "configId": 14227,
      "policyId": "policy-14227",
      "clientIp": "203.0.113.42",
      "hostname": "shop.example.com",
      "path": "/api/v1/login",
      "method": "POST",
      "statusCode": 403,
      "userAgent": "Mozilla/5.0",
      "rule": {
        "id": "rule-INJECTION",
        "name": "SQLi signature",
        "message": "SQL injection detected on /api/v1/login",
        "severity": "CRITICAL",
        "category": "INJECTION"
      },
      "action": "DENY",
      "geoLocation": { "country": "US", "city": "New York" },
      "requestSize": 256,
      "responseSize": 512
    }
  ]'
```

`201 Created`:

```json
{ "acceptedCount": 1 }
```

`400 Bad Request` on a validation failure (per-field detail, indexed by batch position):

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "one or more events are invalid",
    "details": [
      { "field": "[0].clientIp", "message": "must not be blank" },
      { "field": "[0].rule",     "message": "must not be null" }
    ]
  }
}
```

A body that cannot be parsed at all returns `code: "MALFORMED_REQUEST"`.

**Enums** (validated on ingest):
`rule.category` ∈ `INJECTION, XSS, PROTOCOL_VIOLATION, DATA_LEAKAGE, BOT, DOS, RATE_LIMIT` ·
`rule.severity` ∈ `CRITICAL, HIGH, MEDIUM, LOW` ·
`action` ∈ `DENY, ALERT, MONITOR`.

### `GET /v1/stats/summary`  (analytics :8084)

Aggregates over an optional config + time range. All query params are optional;
omitting `configId` aggregates across all configs, omitting `from`/`to` uses the
full available range.

| Param | Meaning |
|-------|---------|
| `configId` | restrict to one config |
| `from`, `to` | ISO-8601 instants bounding `timestamp` |

```bash
curl -s "localhost:8084/v1/stats/summary?configId=14227&from=2026-07-07T00:00:00Z&to=2026-07-08T00:00:00Z"
```

```json
{
  "configId": 14227,
  "timeRange": { "from": "2026-07-07T00:00:00Z", "to": "2026-07-08T00:00:00Z" },
  "totalEvents": 6,
  "byCategory": {
    "INJECTION": { "count": 6, "avgThreatScore": 77.5 }
  },
  "byAction": { "DENY": 6 },
  "topAttackers": [
    { "clientIp": "203.0.113.42", "count": 6, "avgThreatScore": 77.5 }
  ],
  "topTargetedPaths": [
    { "path": "/api/v1/login", "count": 6 }
  ]
}
```

`topAttackers` and `topTargetedPaths` are the top 10 by count (ties broken by
key ascending); `avgThreatScore` is rounded to one decimal.

### `GET /v1/events/samples`  (analytics :8084)

Filtered, paginated enriched records, sorted by `timestamp` **descending**,
with a `total` count of all matches.

| Param | Default | Notes |
|-------|---------|-------|
| `configId`, `from`, `to` | — | same as stats |
| `category` | — | one `AttackCategory` |
| `action` | — | one `Action` |
| `limit` | 20 | clamped to `[1, 100]` |
| `offset` | 0 | for paging |

```bash
curl -s "localhost:8084/v1/events/samples?category=INJECTION&limit=1"
```

```json
{
  "total": 6,
  "limit": 1,
  "offset": 0,
  "results": [
    {
      "eventId": "evt-0006",
      "timestamp": "2026-07-07T09:05:00Z",
      "configId": 14227,
      "policyId": "policy-14227",
      "clientIp": "203.0.113.42",
      "hostname": "shop.example.com",
      "path": "/api/v1/login",
      "method": "POST",
      "statusCode": 403,
      "userAgent": "Mozilla/5.0",
      "rule": {
        "id": "rule-INJECTION",
        "name": "SQLi signature",
        "message": "SQL injection detected on /api/v1/login",
        "severity": "CRITICAL",
        "category": "INJECTION"
      },
      "action": "DENY",
      "geoLocation": { "country": "US", "city": "New York" },
      "requestSize": 256,
      "responseSize": 512,
      "attackType": "SQL/Command Injection",
      "threatScore": 90,
      "receivedAt": "2026-07-07T09:05:00.120Z"
    }
  ]
}
```

## Threat scoring

Scoring is deterministic and additive, capped at 100. It is driven by a small
**data-driven rule engine** in the enrichment service: rules are rows (not code)
of `type = "SCORING"` — `(fact_key, operator, operand) → points` — that the engine
sums over the matched rules. The default rules reproduce the matrix below, and a
user can add/edit/disable one at runtime via the enrichment **`/v1/rules`
REST API** (or its editor panel in the Simulation Console) — the engine re-reads
rules per event, so a change applies to the next event with no restart. Storage
is `wsa.rules=inmemory` (the built-in defaults) or `postgres` (a seeded `rules`
table). The engine itself is subject-agnostic — scoring is just one rule `type`.

| Component | Contribution |
|-----------|--------------|
| **Severity** | `CRITICAL` +40 · `HIGH` +30 · `MEDIUM` +20 · `LOW` +10 |
| **Action** | `DENY` +20 · `ALERT` +10 · `MONITOR` +0 |
| **Sensitive path** | path contains `/admin` or `/login` → +15 |
| **Repeat offender** | **> 5 events from the same `clientIp` in the last 10 minutes** → +15 |
| **Cap** | total clamped to `100` |

`attackType` is a fixed classification of `rule.category`: `INJECTION` →
"SQL/Command Injection", `XSS` → "Cross-Site Scripting", `PROTOCOL_VIOLATION` →
"Protocol Anomaly", `DATA_LEAKAGE` → "Data Exfiltration", `BOT` → "Bot
Activity", `DOS` → "Denial of Service", `RATE_LIMIT` → "Rate Limiting".

The repeat-offender check is the one **stateful** part of scoring: it counts the
client IP's events in a sliding window at ingest time (record-then-read; the 6th
event from an IP is the first to be flagged). That window lives behind the
`OffenderWindow` port — in-memory for dry runs, Redis (per-IP sorted set with a
TTL) under load.

## Storage — the `wsa.storage` switch

Every persistence concern sits behind a port, and each service selects its
adapter at runtime with a single property (`wsa.storage`, or env `WSA_STORAGE`).
Adapters are gated by `@ConditionalOnProperty`; the matching database
auto-configuration is gated by an `AutoConfigurationImportFilter`, so a service
running in a non-matching mode opens **no** connection to that database.

| `wsa.storage` | enrichment (offender window) | event-store / analytics (event store) |
|---------------|------------------------------|----------------------------------------|
| `inmemory` (default) | in-memory sliding window | in-memory map |
| `redis` | Redis sorted-set window + dedup | (pairs with postgres for the store) |
| `postgres` | in-memory window | shared Postgres `events` table |

The default Compose stack runs enrichment on **Redis** and event-store +
analytics on **PostgreSQL** (event-store is the sole writer; analytics reads the
same table read-only, standing in for a read replica). Point a service at
another backend with env vars, e.g.
`WSA_STORAGE=postgres SPRING_DATASOURCE_URL=jdbc:postgresql://…`.

### Why this storage stack

PostgreSQL was chosen **after benchmarking it head-to-head against MongoDB** —
both were implemented behind the same ports and driven with an identical seeded
100k-event workload (full method + results in
[`docs/storage-benchmark.md`](docs/storage-benchmark.md)).

| Metric (100k, medians) | MongoDB | PostgreSQL |
|---|--:|--:|
| `/v1/stats/summary` p95 (aggregation) | 1,468 ms | **431 ms** (3.4×) |
| `/v1/events/samples` p95 | 106 ms | **36 ms** (2.9×) |
| Write persist rate | 1,104 ev/s | **1,302 ev/s** (1.18×) |

For this aggregation-heavy analytics workload, a relational store with
`(config_id, timestamp)` / `(client_ip, timestamp)` indexes beat Mongo's
aggregation pipeline by ~3× on the query hot path and ~18% on writes, and it
scales with Kafka partitions + consumer replicas up to a measured **~2,200 ev/s
knee** (4 replicas / 4 partitions) before the single host becomes CPU-bound.
**Redis** stays for the enrichment offender window — a sliding-window counter, a
different concern from the event store. The Mongo adapter and the full benchmark
harness are preserved on the `candidate/mongo-store` branch.

## Data generator

Part 5 is a seedable CLI (`services/event-generator`) that produces realistic
events and attack-wave bursts (repeated IP+path) and can POST them straight to
the gateway. It is deterministic — a given seed reproduces the same dataset.

```bash
mvn -pl services/event-generator spring-boot:run \
  -Dspring-boot.run.arguments="\
    --wsa.generator.total-events=10000 \
    --wsa.generator.wave-count=5 \
    --wsa.generator.output-mode=HTTP \
    --wsa.generator.target-url=http://localhost:8081"
```

`output-mode` is `HTTP` (feed the gateway), `JSON_FILE`, or `STDOUT`.

## Testing

- **Unit tests** cover the scoring matrix and classification edge cases (the
  explicitly-graded deterministic logic) and the analytics aggregations.
- **Integration tests** use **Testcontainers** against real Postgres / Redis,
  so adapter behavior is verified against the actual engines rather
  than mocks. They require a running Docker daemon; `mvn verify` runs them.
- Cross-service serialization is exercised end-to-end (gateway → enrichment →
  event-store) over an embedded broker.

## Project layout

```
services/
  contracts/        shared Kafka message API + shared store schema (source of truth)
  gateway/          :8081  ingestion + validation → events.raw
  enrichment/       :8082  classify + score → events.enriched
  event-store/      :8083  persist enriched events (sole writer)
  analytics/        :8084  stats + samples (read-only)
  event-generator/  CLI    synthetic events + attack waves
docs/               requirements, effort estimate, SDD
```

| Doc | Purpose |
|-----|---------|
| [`docs/01-requirements.md`](docs/01-requirements.md) | Problem statement, functional & non-functional requirements |
| [`docs/03-sdd.md`](docs/03-sdd.md) | System Design Document (distributed architecture, scaling) |
| [`docs/storage-benchmark.md`](docs/storage-benchmark.md) | Mongo-vs-Postgres benchmark + scaling sweep (storage justification) |
| [`AGENTS.md`](AGENTS.md) | Engineering guidelines & conventions |

## Bonus features

Beyond the five required parts, these optional challenges are implemented (curl
examples in the owning service's README):

- **B2 — Streaming ingestion.** In addition to REST, the gateway consumes events
  from the Kafka topic `events.ingest` (same validation, republished to
  `events.raw`) — a second inbound adapter over one shared `EventIngestionService`.
  Producer script: `scripts/produce-to-kafka.sh`. → [`services/gateway`](services/gateway)
- **B3 — Time-series.** `GET /v1/stats/timeseries?interval={1m|5m|1h}` — event
  counts bucketed by interval. → [`services/analytics`](services/analytics)
- **B1 — Alerting.** `POST /v1/alerts/define` + `GET /v1/alerts/evaluate` —
  "N events of category X within Y minutes" → firing alerts. → [`services/analytics`](services/analytics)
- **B4 — Rate limiting.** `429` on `/v1/stats` + `/v1/events` when a client IP
  exceeds the configured rate (`wsa.rate-limit`). → [`services/analytics`](services/analytics)

## What I'd improve with more time

- **Dead-letter handling.** A poison message on `events.raw`/`events.enriched`
  currently retries against the same consumer indefinitely. The next increment
  is a dead-letter topic with bounded retries and a small requeue/inspect path,
  so one malformed event can't stall a partition.
- **Idempotency at the edge.** De-duplication today relies on the event-store's
  write being keyed by `eventId` (first-write-wins). A duplicate detected
  at ingest — or an explicit idempotency key — would save the wasted enrichment
  and Kafka round-trip.
- **Schema Registry + Avro.** Messages are JSON for now. Moving the contracts to
  Avro with a Schema Registry would make the cross-service compatibility rules
  enforceable at build time instead of by convention.
- **Richer observability.** Structured logs carry the correlation id; the
  natural next step is tracing spans across the Kafka hops and per-stage metrics
  (ingest rate, enrichment latency, consumer lag).

## What was challenging

- **Keeping the domain pure while swapping databases.** The interesting design
  work was making one scoring/analytics implementation run unchanged over four
  storage backends. Getting the auto-configuration gating right — so a service
  in `inmemory` mode makes *zero* attempts to reach a database it isn't
  configured for — took an `AutoConfigurationImportFilter` rather than the simpler
  `spring.autoconfigure.exclude`, which cannot be cleared by an environment
  variable at runtime.
- **Closing the analytics loop across processes.** Splitting into services means
  analytics can't just read enrichment's memory. The fix was a shared store
  contract (event-store writes, analytics reads the same collection/table) —
  which then had to produce identical aggregation results whether backed by a
  SQL `GROUP BY` or the in-memory reference implementation (and, during the
  storage evaluation, Mongo's aggregation pipeline) — tie-breaks included.
- **Deterministic scoring with a stateful rule.** The repeat-offender bonus is a
  read-during-write against a sliding window — easy in memory, but it is exactly
  the part with real performance implications at scale, which is why it sits
  behind its own port with a Redis adapter.
- **Testcontainers vs. the local Docker Engine.** A Docker API-version mismatch
  meant the client defaulted to a version the daemon rejected; pinning the
  Testcontainers API version at the reactor level was needed to get integration
  tests running reliably.
