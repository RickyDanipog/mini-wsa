# Mini WSA — Software Design Document (SDD)

> Status: **v2 — distributed services** · Date: 2026-07-07 · Owner: Ricky
> Companion to `01-requirements.md` and `storage-benchmark.md`.
>
> **This v2 supersedes the earlier single-service (`mini-wsa` monolith) design.**
> Mini WSA is now an **event-driven pipeline of 5 independent services** connected
> by Kafka. The domain logic specced earlier (scoring, classification,
> aggregation, validation) is reused, but re-homed into the owning service.

---

## 1. Design Goals & Principles

- **Service autonomy.** Each service is independently deployable, owns its data,
  and communicates through explicit contracts — never by reaching into another
  service's internals.
- **Asynchronous by default on the write path.** Ingestion → enrichment →
  storage flows through Kafka topics, so each stage scales and fails
  independently and back-pressure is natural.
- **Hexagonal *inside* each service.** Every service keeps a pure domain core
  behind ports; Kafka/HTTP/DB are adapters. The `domain` package of any service
  imports no Spring/Kafka/storage types.
- **Explicit contracts.** Cross-service messages use versioned schemas in a
  shared `contracts` module — the only thing services share.
- **Trace everything.** A `correlationId` is created at the gateway and
  propagated on every Kafka message and HTTP hop.
- **In-memory first, real stores later.** Each service ships in-memory adapters
  for dry runs, then swaps in its real store (PostgreSQL/Redis) behind the same
  port; `event-store` and `analytics` run on PostgreSQL, chosen over Mongo after
  a comparative load test (§7, `storage-benchmark.md`).

> **Tradeoff acknowledged.** This is deliberately more than a take-home strictly
> needs (the assignment favors "working software over perfection"). It is chosen
> to demonstrate a scalable, async architecture. Two known smells are accepted
> consciously: a dedicated storage service (§3), and `analytics` sharing
> `event-store`'s schema via a read replica (§7).

---

## 2. System Context (C4 level 1)

```
   ┌──────────────────┐   REST POST /v1/events/ingest    ┌───────────────────────────┐
   │  event-generator │ ───────────────────────────────▶ │                           │
   │   (test tool)    │   (single / batch / stream)      │        MINI  WSA          │
   └──────────────────┘                                   │  (5 services + Kafka)     │
                                                          │                           │
   ┌──────────────────┐   GET /v1/stats/summary          │                           │
   │  Analyst / SOC   │   GET /v1/stats/timeseries        │                           │
   │  dashboard, curl │   GET /v1/events/samples          │                           │
   └──────────────────┘ ◀──────────────────────────────▶ │                           │
                                                          └───────────────────────────┘
```

External actors: the **event-generator** (producer of test load), **analysts /
tools** (consumers of analytics). Everything else is internal.

---

## 3. Service Architecture (C4 level 2)

```
 ┌───────────────┐  REST   ┌───────────────┐  events.raw   ┌───────────────┐  events.enriched
 │ event-        │ ──────▶ │   gateway     │ ────────────▶ │  enrichment   │ ────────────┐
 │ generator     │         │  :8081        │   (Kafka)     │  :8082        │  (Kafka)     │
 │ (CLI)         │         │ validate,     │               │ classify+score│              │
 └───────────────┘         │ transform,    │               │ Redis window  │              ▼
                           │ publish       │               └──────┬────────┘      ┌───────────────┐
                           └───────────────┘                      │ reads/writes  │  event-store  │
                                                                   ▼               │  :8083        │
                                                            ┌─────────────┐        │ persist       │
                                                            │   Redis     │        │ (system of    │
                                                            │ offender    │        │  record)      │
                                                            │ window      │        └──────┬────────┘
                                                            └─────────────┘          writes│
                                                                                          ▼
                                                                                   ┌──────────────┐
   ┌───────────────┐   GET /v1/stats/*, /v1/events/samples                         │  PostgreSQL  │
   │ Analyst/tools │ ────────────────────────────────▶  ┌───────────────┐  reads   │   primary    │
   └───────────────┘                                    │  analytics    │ ◀──────── read replica
                                                         │  :8084        │
                                                         └───────────────┘
```

### Services & responsibilities

| Service | Port | Inbound | Outbound | Owns |
|---------|------|---------|----------|------|
| **gateway** | 8081 | REST `POST /v1/events/ingest` (later Kafka/Spark) | publishes `events.raw` | request validation, DTO→contract transform, correlationId minting |
| **enrichment** | 8082 | consumes `events.raw` | publishes `events.enriched`; reads/writes Redis | classification, threat scoring, repeat-offender window (Redis) |
| **event-store** | 8083 | consumes `events.enriched` | writes PostgreSQL primary | the enriched-event system of record |
| **analytics** | 8084 | REST `GET /v1/stats/*`, `/v1/events/samples` | reads PostgreSQL read replica | aggregation + sample queries (read side) |
| **event-generator** | — (CLI) | — | REST → gateway (later Kafka producer) | realistic events + attack waves |
| **contracts** | — (lib) | — | — | shared Kafka message schemas + envelope |

**Why a dedicated `event-store` service (accepted smell):** it isolates the
system-of-record and lets storage scale/evolve independently; the cost is a
network hop and a thin service. Documented as a conscious choice.

### Hexagonal layering *within* each service
Every service repeats the same internal shape:
```
interfaces (REST controllers / Kafka listeners)  →  application (use cases)  →  domain (model + ports)
                                                                   ▲
infrastructure (Kafka producers, PostgreSQL/Redis adapters) implements the ports
```
So the ports we built (`EventStore`, `OffenderWindow`, the use-case interfaces)
live inside their owning service; `contracts` holds only the wire DTOs.

---

## 4. Messaging Contracts (`contracts` module)

- **Topic `events.raw`** — a validated, untransformed event leaving the gateway.
  Key = `clientIp` (so one attacker's events land on one partition, preserving
  per-IP order for the window). Value = `RawEventMessage`.
- **Topic `events.enriched`** — an enriched event leaving enrichment. Key =
  `configId` (aggregation locality). Value = `EnrichedEventMessage`.
- **Envelope** — every message carries `correlationId`, `occurredAt`, and a
  schema `version`. Consumers tolerate unknown newer fields (forward-compatible).
- **Schema evolution** — additive-only fields; breaking changes = a new topic
  version. Serialization: JSON to start (readable, simple); note Avro/Schema
  Registry as the hardening step.

---

## 5. Data Flow

### 5.1 Write path (async, across services)
```
generator/client ─POST /v1/events/ingest─▶ gateway
   gateway: bean-validate · mint correlationId · map DTO→RawEventMessage · publish events.raw · return 201/400
      └▶ enrichment consumes events.raw
            · classify attackType (AttackType.fromCategory)
            · repeatOffender = redisOffenderWindow.countRecent(clientIp, 10m, receivedAt) > 5
            · threatScore = ruleBasedCalculator.calculate(inputs)   [pure]
            · record this event into the Redis window
            · publish EnrichedEventMessage → events.enriched
               └▶ event-store consumes events.enriched · persist to PostgreSQL primary
```
Validation failures are rejected synchronously at the gateway (201/400 to the
caller) — only valid events ever reach Kafka.

### 5.2 Read path (analytics)
```
analyst ─GET /v1/stats/summary?configId&from&to─▶ analytics
   analytics: query PostgreSQL read replica · aggregate (byCategory/byAction/topAttackers/topPaths) · 200
analyst ─GET /v1/events/samples?...&limit&offset─▶ analytics
   analytics: filter + sort desc + page + total · 200
```

---

## 6. Enrichment Logic (in `enrichment`)

Unchanged in substance from the earlier design, now owned by the enrichment service.

### 6.1 Classification & scoring
`AttackTypeClassifier.classify(category)` → `AttackType`. Scoring is a
**data-driven rule engine** (`ruleengine` package): a scoring rule is a *row*,
not a class — `(fact_key, operator, operand) → points` — evaluated against
generic `Facts`, a dotted-path view over the event (`rule.severity`,
`geoLocation.country`, `offenderEventCount`, …). `RuleEngineThreatScoreCalculator`
sums the outputs of all matching enabled rules and **caps at 100**. Rules live
behind the `ScoringRuleRepository` port (`wsa.rules=inmemory` built-in defaults,
or `postgres` — a seeded `rules` table) and are re-read per event, so
add/edit/disable via the enrichment `/v1/rules` API takes effect on the next
event — no restart, no code change. The seeded defaults reproduce the assignment
matrix: severity 40/30/20/10, action 20/10/0, sensitive path +15, repeat
offender +15. Given `Facts`, the engine is I/O-free, keeping the graded scoring
logic exhaustively unit-testable.

### 6.2 Repeat-offender window (Redis, owned by enrichment)
Because enrichment no longer stores events itself (event-store does), the
`OffenderWindow` port owns its own window state with two methods:
`recordEvent(clientIp, receivedAt)` and `countRecentEventsFromClient(clientIp,
window, asOf)`. Implementation = **Redis sorted set per IP** (`ZADD receivedAt`,
`ZREMRANGEBYSCORE` old, `ZCOUNT [asOf-10m, asOf]`, TTL). In-memory adapter for
tests; Redis for real. Self-contained in enrichment — no synchronous call back
to event-store.

**Locked semantics (record-then-read):** for each event, `recordEvent` first,
then count the window **including the current event**; flag `repeatOffender =
count > 5`. So the **6th** event from an IP within the 10-minute window is the
first one to earn +15. Window basis = **`receivedAt`** (trusted clock); window =
**10 min**. (This is the reading of "more than 5 events exist within the last 10
minutes" where the event being scored is one of those that exist.)

---

## 7. Storage & Data Ownership

**Decision: PostgreSQL is the shipped backend.** Both Mongo and Postgres were
implemented behind the `EventStore` / `AnalyticsReadStore` ports and benchmarked
under a fixed, seeded load; **Postgres won and is now the system of record.** The
Mongo adapter is removed from `main` (preserved on the `candidate/mongo-store`
branch). The in-memory adapter remains the **default for dev/tests**. The
architecture is unchanged either way — event-store is the sole writer, analytics
reads the same store read-only (a read replica in production).

| Store | Owner | Purpose | Path |
|-------|-------|---------|------|
| **PostgreSQL primary** | event-store | enriched-event system of record | in-memory (dev/tests) → PostgreSQL |
| **PostgreSQL read replica** | analytics (read-only) | aggregation + sample reads | shares event-store schema (accepted coupling) |
| **Redis** | enrichment | repeat-offender sliding window | in-memory → Redis |

Event data is **append-only, timestamped**. The `events` table is indexed on
`(config_id, timestamp)`, `(client_ip, timestamp)`, and `(timestamp)` to drive
the aggregation and sample queries. Locally the "replica" is one Postgres
instance analytics connects to read-only; a true read replica is the production
shape. ClickHouse remains a **words-only** scaling talking point (§10).

### 7.1 Why Postgres — benchmark evidence

The choice was **benchmark-driven**, not a default. Both stores ran the same
100k-event workload on a single laptop-class host (3-run medians); full method
and results in `storage-benchmark.md`. Headline:

| Metric | Postgres | Mongo | Result |
|--------|----------|-------|--------|
| `/v1/stats/summary` p95 | **431 ms** | 1468 ms | 3.4× faster |
| `/v1/stats/summary` p99 | **492 ms** | 1625 ms | — |
| `/v1/events/samples` p95 | **36 ms** | 106 ms | 2.9× faster |
| Write persist rate | **1302 ev/s** | 1104 ev/s | ~18% faster |

Postgres won on every axis that matters here — the stats aggregation (the read
that hurts) most decisively — so it ships and Mongo was retired to a branch.

---

## 8. API Surface

- **gateway (write):** `POST /v1/events/ingest` — single or array; bean-validated
  (required fields, enum binding, ISO-8601); **all-or-nothing** → `201
  {acceptedCount}` or `400 {error:{code,message,details[]}}`; mints correlationId
  (`receivedAt` is stamped in enrichment on processing). The same ingestion core
  also consumes the Kafka topic `events.ingest` (bonus B2) — a second inbound
  adapter over `EventIngestionService`; invalid messages are logged and skipped.
- **enrichment (rules):** `GET/POST/PUT/DELETE /v1/rules` (+ `GET
  /v1/rules/options`) — CRUD over the scoring rules the engine evaluates;
  changes apply on the next event. Operator/fact-key validated → `400 {details}`.
- **analytics (read):** `GET /v1/stats/summary`, `GET /v1/events/samples`
  (+ bonus B3 `GET /v1/stats/timeseries?interval={1m|5m|1h}`) — exact assignment
  JSON shapes, no `{success,data}` envelope, pagination `limit` default 20 /
  max 100 + `offset` + `total`.
- **analytics (alerts, bonus B1):** `POST /v1/alerts/define` + `GET
  /v1/alerts/evaluate` — threshold rules over category counts in a window.
- **analytics (rate limit, bonus B4):** stats/samples endpoints return `429`
  above the configured per-client-IP rate (`wsa.rate-limit`).

---

## 9. Cross-Cutting Concerns

- **correlationId:** minted at gateway (`x-correlation-id` header or generated),
  carried in every Kafka envelope and stamped on logs across all services — the
  join key for tracing the async pipeline.
- **Validation** happens once, at the gateway edge; downstream services trust
  the contract (and still guard domain invariants in value-object constructors).
- **Error handling:** typed exceptions → `@RestControllerAdvice` at gateway/
  enrichment/analytics. Kafka consumers: the gateway `events.ingest` listener
  logs-and-skips invalid payloads; duplicate `eventId`s are dropped in
  enrichment via `ProcessedEventLog`. A dead-letter topic with bounded retries
  is deliberately future work (see README "What I'd improve").
- **Logging:** single-line structured logs with correlationId as the third arg.
- **Config:** topic names, Redis URI, datasource URL, window size, thresholds,
  page caps in `application.yml` per service.

---

## 10. Scalability (now genuinely distributed)

| Concern | Lever |
|---------|-------|
| Ingestion spikes | gateway is stateless → scale horizontally behind an LB; Kafka absorbs bursts |
| Enrichment throughput | scale the enrichment **consumer group**; partition `events.raw` by `clientIp` (keeps per-IP window correct on one partition) |
| Repeat-offender at scale | Redis sliding window (shared, O(log n)) instead of per-instance state |
| Storage write load | event-store consumer group; Postgres partitioning; **columnar OLAP (ClickHouse)** as the analytics-at-100× story |
| Read load | analytics scales independently off the read replica; pre-aggregated rollups / cache hot ranges |
| Isolation | a slow/failed stage back-pressures via Kafka lag instead of cascading failure |

### 10.1 Measured scaling (single host)

The scaling sweep varied Kafka partitions against enrichment + event-store
consumer replicas (`storage-benchmark.md`). The pipeline **does** scale with
partitions + matching consumer replicas, up to a measured **knee of ~2200 ev/s
at 4 replicas / 4 partitions**. Past that point growth is **sub-linear (1.7×,
not 4×)** because a single host is CPU-bound once ~4 replicas are busy.
Partitions without matching consumers give no gain; replicas beyond the
partition count sit idle. These absolute numbers are single-host — the
**transferable result is the shape**: throughput tracks `min(partitions,
consumers)` until the underlying host saturates.

To go **10×**, spread the same consumer groups across more hosts and add
partitions in lockstep; the read side scales independently off the read replica.
At **100×**, the write path stays Kafka + Postgres but the analytics read path
moves to a **columnar OLAP store (ClickHouse)** — still words-only, not built.

---

## 11. Testing Strategy

- **Per-service unit tests:** enrichment scoring matrix (graded), classifier,
  offender-window boundaries; gateway validation + mapping; analytics
  aggregation + paging.
- **Per-service integration (Testcontainers):** gateway→Kafka publish;
  enrichment consume→enrich→publish (embedded/Testcontainers Kafka + Redis);
  event-store consume→persist (PostgreSQL); analytics read (PostgreSQL).
- **Contract tests:** producer/consumer agree on the `contracts` schemas.
- **End-to-end (compose):** generator → full pipeline → analytics query returns
  the expected aggregates.

---

## 12. Deployment (`docker-compose`)

Brings up: `kafka` (single-broker KRaft), `postgres`, `redis`, the four service
containers (`gateway:8081`, `enrichment:8082`, `event-store:8083`,
`analytics:8084`; `event-generator` runs on demand), and `demo-ui:8090` — an
nginx container serving the Simulation Console (a single-file dashboard that
exercises every use case) and reverse-proxying `/api/{gateway,enrichment,analytics}/…`
same-origin. Each service = its own Maven module + Dockerfile.
`docker compose up` = the whole pipeline.

---

## 13. Migration From the Monolith (what we keep vs move)

| Built in `mini-wsa` (v1) | Goes to (v2) |
|--------------------------|--------------|
| enums, `SecurityEvent`/`DataLogRecord`/VOs | `contracts` (wire DTOs) + each service's domain model |
| `AttackTypeClassifier`, `ThreatScoreCalculator`, rules, `ThreatScoringInputs` | **enrichment** |
| `OffenderWindow` port + impl | **enrichment** (Redis) |
| `EventStore` port + in-memory/Postgres adapters | **event-store** |
| stats/samples use-cases + records | **analytics** |
| ingest DTOs, validation, mapper, `IngestSecurityEvents` | **gateway** (+ publish instead of store) |
| `event-generator` module | unchanged (feeds gateway) |

The `mini-wsa` module is retired once its pieces are re-homed. Nothing designed
is wasted — only relocated across service boundaries.

---

## 14. Open Decisions / Risks

1. **Serialization** — JSON now; Avro + Schema Registry is the hardening step (open).
2. **Delivery semantics** — at-least-once consumption → enrichment/store must be
   idempotent on `eventId` (dedup). To design in the per-service plans.
3. **Ordering** — per-IP order preserved by partitioning `events.raw` on
   `clientIp`; cross-IP order not guaranteed (acceptable).
4. **Local "replica"** — single Postgres shared read-only locally; real read replica in prod.
5. **Delivery risk** — 5 services + Kafka is ambitious for the timebox; mitigate
   by shipping the in-memory/single-broker path first and hardening later.
