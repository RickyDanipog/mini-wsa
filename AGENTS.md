# AGENTS.md — Mini WSA Engineering Guidelines

> Living document. These are the conventions any agent (human or AI) follows
> when implementing this repo. **We adapt these on the go** — when a rule stops
> serving us, change it here first, then follow it.
>
> Design context lives in `docs/`:
> `01-requirements.md` · `02-effort-estimate.md` · `03-sdd.md`. Read them first.

---

## 0. What this is

A **mini security analytics pipeline** ("Mini WSA") — an Akamai CSI backend
take-home. Ingests security events (DLRs), classifies/enriches/scores them,
stores them, and serves analytics APIs. Backend only, Java + Spring Boot.

> ⚠️ **This is a standalone external assignment, not a Unipaas service repo.**
> Where the injected Unipaas org rules conflict with the assignment, **the
> assignment wins**. Specific deliberate deviations are called out in §6 and §7.

---

## 1. Tech Stack

| Concern | Choice | Notes |
|---------|--------|-------|
| Language | **Java 21 (LTS)** | records, sealed types, pattern matching, switch expressions |
| Framework | **Spring Boot 3.x** | Web, Validation, Actuator, Test |
| Build | **Maven** (multi-module) | root aggregator + one module per service |
| Storage | **Mongo + Redis** (see `03-sdd.md` §7) | two ports: `EventStore` (Mongo) + `OffenderWindow` (Mongo→Redis). In-memory adapters now; **PostgreSQL** built as a load-test comparison; ClickHouse is words-only. Domain never changes. **Active adapter is selected per service by the `wsa.storage` property** (`inmemory` default via `@ConditionalOnProperty(matchIfMissing=true)`; each new adapter is gated on its own value — `mongo`/`postgres`/`redis` — so all coexist and exactly one is active). |
| Containers | **Docker + Docker Compose** | `docker compose up` brings up full stack |
| IDE | **IntelliJ IDEA CE** | Maven-native; import root `pom.xml` |
| Tests | JUnit 5 · Spring Boot Test / MockMvc · **Testcontainers** | real storage in integration tests |

---

## 2. Repository Layout (mono-repo of services)

> **v2 (distributed).** The `mini-wsa` monolith is being decomposed into 5
> services + a shared `contracts` module (see `03-sdd.md` v2 and the
> `2026-07-07-v2-restructure.md` plan). The monolith code is being relocated,
> not rewritten.

```
WSA/
├── AGENTS.md                  ← you are here
├── README.md                  ← assignment-facing (build/run, API, architecture)
├── docs/                      ← requirements, effort, SDD (v2)
├── docker-compose.yml         ← full stack: kafka + mongo + redis + the 4 services
├── pom.xml                    ← root Maven aggregator / parent
└── services/
    ├── contracts/             ← shared Kafka message schemas + envelope (no runtime)
    ├── gateway/               ← :8081 REST ingest → validate/transform → publish events.raw
    ├── enrichment/            ← :8082 consume events.raw → classify+score (Redis window) → publish events.enriched
    ├── event-store/           ← :8083 consume events.enriched → persist (Mongo, system of record)
    ├── analytics/             ← :8084 read Mongo replica → serve /v1/stats/*, /v1/events/samples
    └── event-generator/       ← CLI tool: realistic events + attack waves; feeds gateway
```

- **One deployable = one service module** under `services/`, each with its own
  `Dockerfile`, wired together by the root `docker-compose.yml`.
- Services communicate only via **Kafka topics** (write path) and the shared
  **`contracts`** module (schemas) — never by importing each other's internals.
  Read path: `analytics` reads a Mongo replica of `event-store`'s data.
- The `mini-wsa` monolith has been retired (Phase 0 Task 5); its enums live in
  `contracts` and each service owns its domain. A thin end-to-end pipeline runs
  over Kafka; per-service business logic is implemented next from each service's
  `.claude/plans/plan.md`.

---

## 3. Architecture — Hexagonal / DDD (per service)

Follows `03-sdd.md`: a **storage-agnostic domain core** behind a repository
**port**, with framework/storage as **adapters**. Dependencies point *inward*:
`interfaces → application → domain`, and `infrastructure → domain` (implements
its ports). **The `domain` package never imports Spring, Jackson, JPA, or any
storage type.**

Each service repeats this shape internally (base `com.akamai.wsa.<service>`);
inbound adapters are REST controllers **and/or** Kafka listeners.

### Package structure (per service, base `com.akamai.wsa.<service>`)

```
com.akamai.wsa.<service>
├── <Service>Application.java
│
├── domain/                    ← pure business model & rules. Zero framework deps.
│   ├── model/                 ← aggregates, entities, value objects, enums
│   ├── service/               ← stateless domain services (the graded logic)
│   └── port/                  ← outbound ports (repository interfaces) OWNED by the domain
│
├── application/               ← use cases: orchestrate domain + ports. Thin.
│   ├── ingest/  · stats/  · samples/
│   └── (command/query models, application services)
│
├── infrastructure/            ← outbound adapters (implement domain ports)
│   ├── persistence/<engine>/  ← EventStore + OffenderWindow impls, storage entities, mappers, migrations
│   └── config/                ← Spring @Configuration, properties
│
└── interfaces/                ← inbound adapters
    ├── rest/                  ← controllers, DTOs, @ControllerAdvice (gateway, analytics)
    └── messaging/             ← @KafkaListener consumers (enrichment, event-store)
```

**Golden rule:** if a class needs a Spring/Kafka or storage import, it belongs in
`infrastructure` or `interfaces`, never in `domain`.

---

## 4. DDD Conventions (tactical patterns)

We stay close to DDD. Concretely:

- **Aggregate root:** `SecurityEvent` — the enriched event. It is the
  consistency boundary; construct it *fully enriched and valid* (no half-built
  aggregates). Immutable after creation.
- **Value Objects (immutable, self-validating, `record`s):** `ThreatScore`
  (0–100, enforces the cap), `ClientIp`, `AttackType`, `Rule`, `GeoLocation`,
  `TimeWindow`. Equality by value. No identity.
- **Enums for closed vocabularies:** `AttackCategory` (INJECTION, XSS,
  PROTOCOL_VIOLATION, DATA_LEAKAGE, BOT, DOS, RATE_LIMIT), `Action`
  (DENY, ALERT, MONITOR), `Severity` (CRITICAL, HIGH, MEDIUM, LOW).
- **Domain services (stateless interfaces):** `AttackTypeClassifier`
  (`classify`), `ThreatScoreCalculator` (`calculate`, pure — offender flag
  injected via `ThreatScoringInputs`). Business rules live here, not in
  controllers or entities-as-anemic-bags.
- **Ports (domain-owned interfaces), two of them:** `EventStore` (`saveAll`,
  `countAll`, `findByConfigId`, and later `summarize`, `findSamples`) and
  `OffenderWindow` (`countRecentEventsFromClient`) — named in the ubiquitous
  language, not in storage terms.
- **Application services** orchestrate a single use case each; they are thin and
  hold no business rules.
- **No anemic domain model:** behavior lives with the data it concerns.
- **Ubiquitous language everywhere** — see §5. Names in code == names in docs ==
  names we'd say to a SOC analyst.

---

## 5. Ubiquitous Language (glossary)

| Term | Meaning |
|------|---------|
| **DLR** | Data Log Record — a raw inbound security event |
| **SecurityEvent** | The *enriched* event (aggregate root): DLR + `attackType` + `threatScore` + `receivedAt` |
| **AttackType** | Human-readable classification derived from `rule.category` |
| **ThreatScore** | Integer 0–100 risk score from the scoring matrix |
| **RepeatOffender** | A `clientIp` with > 5 events in the last 10 minutes |
| **AttackWave** | A burst of events from one IP hitting one path (generator concept) |
| **ConfigId** | Tenant/configuration partition key for queries |
| **receivedAt** | Trusted server-side ingestion timestamp |

---

## 6. Coding Conventions (Java)

- **Immutability by default.** `record`s for VOs/DTOs; no setters on domain
  objects; return new instances, never mutate inputs or shared state.
- **Validation at the boundary.** Bean Validation (`@Valid`, `@NotNull`, enum
  binding, ISO-8601 checks) on inbound REST DTOs. The domain assumes valid input
  and additionally guards invariants in VO constructors.
- **Typed exceptions**, not `throw new Exception("string")`. Domain-specific
  types (e.g. `InvalidEventException`, `ValidationException`) mapped to HTTP by a
  single `@ControllerAdvice`. No string-concatenated messages — structured fields.
- **Pure functions for scored logic.** `ThreatScoreCalculator` and
  `AttackTypeClassifier` take inputs, return outputs, touch no I/O — so they are
  exhaustively unit-testable.
- **Logging: single-line, structured** (borrowed from the org rule — it's good):
  `logger.info("IngestionService - ingest", Map.of("accepted", n), correlationId)`.
  Never log secrets/PII (don't log full payloads). `correlationId` propagated
  from an `x-correlation-id` header (generated if absent) — a nice fit for a SOC
  tracing story.
- **No magic numbers.** Window size (10m), score weights, page caps, thresholds
  live in config/named constants, not inline.
- **Small, focused files.** If a class grows past one clear responsibility, split it.
- **No comments.** The code is the story — tell it with intention-revealing names
  and small, focused methods. No Javadoc, block, or inline comments. If a comment
  feels necessary, that is a signal to rewrite the code, not annotate it.

---

## 7. API Conventions

- **Follow the assignment's documented response shapes exactly** (see
  `docs/01-requirements.md` FR1–FR4). We do **not** wrap in the Unipaas
  `{success,data,error,meta}` envelope — graders run the documented curl
  examples and expect those literal shapes. *(Deliberate deviation from org rule.)*
- **HTTP codes:** 201 ingest success · 400 validation (with `details[]`) ·
  200 queries · 429 (only if rate-limit bonus) · 500 unexpected.
- **Pagination:** `limit` default 20 / max 100 (clamped) · `offset` · `total`
  always present in samples responses.
- **Versioned paths:** `/v1/...` as specified.

---

## 8. Testing

- **Unit (graded, must be thorough):** full `ThreatScoreCalculator` matrix
  (every severity, action, path bump, offender bump, cap-at-100, combinations);
  `AttackTypeClassifier` every category; `OffenderWindow` boundaries
  (exactly 5 vs 6, window edges). Table-driven where possible.
- **Integration (≥1, graded):** ingest → store → query round-trip against **real
  storage via Testcontainers**; validation-error (400) shapes; pagination + total
  correctness; stats aggregation correctness on generator-seeded data.
- **Fakes:** in-memory `EventStore` / `OffenderWindow` implementations for fast service tests.
- Tests are first-class — write them alongside the feature (per part), not after.

---

## 9. Git & Commits

- **Conventional commits**, imperative, lowercase, ≤72-char subject:
  `feat: add ingestion endpoint with batch validation`.
  Types: `feat|fix|refactor|docs|test|chore|perf|ci`. Scope optional.
- **Small, logical commits** — the assignment grades how work is broken down. No
  giant end commits.
- **Milestone tags** as we clear parts: `v0.1-ingestion` → `v0.2-enrichment` →
  `v0.3-stats` → `v0.4-samples` → `v0.5-generator` → bonuses.
- **No** `[UN-XXXX]` ticket prefix and **no** Co-Authored-By trailer (external repo).
- Public GitHub repo; email details to `obokobza@akamai.com` a day before the interview.

---

## 10. Build & Run

- Import the **root `pom.xml`** into IntelliJ (all modules load).
- Build all: `mvn -q clean verify` from repo root.
- Run a service locally: `mvn -pl services/mini-wsa spring-boot:run`.
- Full stack: `docker compose up` (app + storage + any bonus deps).
- Concrete commands land in `README.md` as they're implemented.

---

## 11. Open Decisions (resolve before/at implementation start)

Tracked in `docs/03-sdd.md` §13:
1. ~~**Storage engine**~~ — **RESOLVED:** in-memory adapter first (dry runs); real DBs load-tested behind the port later.
2. **Repeat-offender basis** — event `timestamp` vs `receivedAt`; count vs persisted events only.
3. **Batch failure semantics** — partial-accept (recommended) vs all-or-nothing.
4. **Bonus selection** — recommended B3 (time-series) + B4 (rate limit); B1 stretch; B2 last.
