# v2 Restructure — Services + Kafka Skeleton (Phase 0)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Restructure the repo from the `mini-wsa` monolith into the v2 layout — a shared `contracts` module plus five service modules — and stand up a **thin end-to-end pipeline** over Kafka (gateway publishes → enrichment consumes/republishes → event-store consumes/persists in-memory → analytics reads) so every seam is proven before real logic lands.

**Architecture:** See `docs/03-sdd.md` v2. Event-driven; Kafka topics `events.raw` and `events.enriched`; per-service hexagonal layering; in-memory stores first.

**Tech Stack:** Java 21, Spring Boot 3.3.5, `spring-kafka`, Maven multi-module, Testcontainers (Kafka), single-broker KRaft Kafka + Mongo + Redis via docker-compose.

## Global Constraints

- Base package per service: `com.akamai.wsa.<service>` (e.g. `com.akamai.wsa.gateway`). `contracts` = `com.akamai.wsa.contracts`.
- Ports: gateway 8081 · enrichment 8082 · event-store 8083 · analytics 8084 · generator = CLI.
- Topics: `events.raw` (key=clientIp), `events.enriched` (key=configId). Names in config, not literals.
- Every Kafka message carries an envelope with `correlationId`, `occurredAt`, `version`.
- `domain` packages import no Spring/Kafka/storage. Records, immutability, full parameter names, conventional commits.
- The `mini-wsa` module's code is **relocated, not rewritten** — move classes to their owning service (SDD §13 map).

---

### Task 1: Add the `contracts` module (shared message schemas)

**Files:**
- Create: `services/contracts/pom.xml` (plain jar, parent `mini-wsa-parent`, no Spring runtime — just Jackson annotations if needed)
- Create: `services/contracts/src/main/java/com/akamai/wsa/contracts/MessageEnvelope.java`
- Create: `.../contracts/RawEventMessage.java`, `EnrichedEventMessage.java`, `RuleMessage.java`, `GeoLocationMessage.java`
- Test: `.../contracts/src/test/java/com/akamai/wsa/contracts/MessageSerializationTest.java`
- Modify: root `pom.xml` `<modules>` — add `services/contracts`

**Interfaces / Produces:** the wire DTOs every service (de)serializes. `RawEventMessage` = the validated DLR fields (flat, JSON-friendly: eventId, timestamp, configId, policyId, clientIp, hostname, path, method, statusCode, userAgent, rule{id,name,message,severity,category}, action, geoLocation{country,city}, requestSize, responseSize). `EnrichedEventMessage` = `RawEventMessage` fields + `attackType`, `threatScore`, `receivedAt`. `MessageEnvelope<T>` = `{ correlationId, occurredAt, version, payload }`.

- [ ] **Step 1: Write the failing test** — round-trip a `RawEventMessage` and an `EnrichedEventMessage` through Jackson (assert all fields survive; enums serialize as names; timestamps ISO-8601). Run: `mvn -q -pl services/contracts test` → FAIL.
- [ ] **Step 2:** Create the module pom (packaging jar; dependency `com.fasterxml.jackson.core:jackson-databind` provided/optional) and register in root `pom.xml`.
- [ ] **Step 3:** Add the records (enums as String on the wire or shared enums — decide: keep enums in `contracts` so services share the closed vocabulary; move `AttackCategory`/`Action`/`Severity` here). Full field records, no behavior.
- [ ] **Step 4:** Run test → PASS.
- [ ] **Step 5:** Commit — `feat(contracts): add shared Kafka message schemas`.

---

### Task 2: Scaffold the five service modules (bootable, registered)

Repeat this **template** for each of `gateway`, `enrichment`, `event-store`, `analytics` (the four Spring services); `event-generator` already exists (Part 5 plan) and only needs its target repointed to gateway.

**Files (per service `<svc>`):**
- Create: `services/<svc>/pom.xml` (parent `mini-wsa-parent`; deps per role — see table), `Dockerfile`
- Create: `.../<svc>/src/main/java/com/akamai/wsa/<svc>/<Svc>Application.java`
- Create: `.../<svc>/src/main/resources/application.yml` (its port + kafka/store config)
- Test: a `@SpringBootTest` context-loads test (or `@WebMvcTest` ping for gateway/analytics)
- Modify: root `pom.xml` `<modules>`

**Dependencies per service:**
| Service | starters |
|---------|----------|
| gateway | web, validation, actuator, **kafka** (producer) |
| enrichment | actuator, **kafka** (consumer+producer), **data-redis** |
| event-store | actuator, **kafka** (consumer), **data-mongodb** |
| analytics | web, validation, actuator, **data-mongodb** |

**Template application class** (substitute `<Svc>`/`<svc>`):
```java
package com.akamai.wsa.<svc>;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class <Svc>Application {
    public static void main(String[] args) {
        SpringApplication.run(<Svc>Application.class, args);
    }
}
```

- [ ] **Step 1–4 (per service):** write context-load test → FAIL → add pom + app + `application.yml` (with `server.port`) → PASS. gateway/analytics also get a `/v1/ping` `@RestController` + `@WebMvcTest` (reuse the pattern from the v0.0 skeleton plan).
- [ ] **Step 5:** After all four load, register all in root `pom.xml` and run `mvn -q clean verify` (whole reactor green).
- [ ] **Step 6:** Commit — `chore: scaffold gateway/enrichment/event-store/analytics service modules`.

> Note: to keep the reactor green while services are empty of Kafka wiring, disable Kafka auto-config in tests (`@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration")`) or use `@EmbeddedKafka` only where a listener exists.

---

### Task 3: docker-compose — Kafka + Mongo + Redis + services

**Files:** Modify `docker-compose.yml`; create each service `Dockerfile` (multi-stage, per the v0.0 skeleton template, adjusting the module path).

- [ ] **Step 1:** Add infra services: `kafka` (single-broker KRaft, e.g. `apache/kafka` or `bitnami/kafka`), `mongo:7`, `redis:7`. Expose kafka `9092`, mongo `27017`, redis `6379`.
- [ ] **Step 2:** Add the four service containers wired to infra via env (`SPRING_KAFKA_BOOTSTRAP_SERVERS`, `SPRING_DATA_MONGODB_URI`, `SPRING_DATA_REDIS_HOST`), `depends_on` infra, ports 8081–8084.
- [ ] **Step 3:** `docker compose up --build -d`; verify each `/actuator/health` is UP; `docker compose down`.
- [ ] **Step 4:** Commit — `chore: compose Kafka, Mongo, Redis, and the four services`.

---

### Task 4: Thin end-to-end pipeline skeleton (prove the seams)

Wire the smallest possible flow through all four services using `contracts` DTOs and **in-memory** stores — no real scoring/aggregation yet.

- [ ] **Step 1 (gateway):** `POST /v1/events/ingest` validates minimally, wraps a `RawEventMessage` in an envelope (mint correlationId), publishes to `events.raw` via `KafkaTemplate`; returns `201 {acceptedCount}`. Integration test with `@EmbeddedKafka` asserts a message is published.
- [ ] **Step 2 (enrichment):** `@KafkaListener` on `events.raw` → build an `EnrichedEventMessage` with placeholder `attackType=fromCategory`, `threatScore=0`, `receivedAt=now(clock)` → publish `events.enriched`. `@EmbeddedKafka` test: raw in → enriched out (correlationId preserved).
- [ ] **Step 3 (event-store):** `@KafkaListener` on `events.enriched` → save into an in-memory `EventStore` adapter (reuse the port from v1). Test: consume → `countAll()` increments.
- [ ] **Step 4 (analytics):** `GET /v1/events/samples` returns from an in-memory read (seeded), proving the read endpoint shape. (Real Mongo-replica read comes in the analytics service plan.)
- [ ] **Step 5:** End-to-end compose smoke: generator posts one event → poll analytics until it appears. Document in README.
- [ ] **Step 6:** Commit + tag — `feat: thin end-to-end pipeline across services` , tag `v0.1-pipeline-skeleton`.

---

### Task 5: Retire the `mini-wsa` module (relocate its code)

- [ ] **Step 1:** Move domain/value objects/enums into `contracts` (wire types) and each service's `domain` package per SDD §13. Move scoring/classifier/rules → enrichment; `EventStore` port + in-memory adapter → event-store; stats/samples records + use cases → analytics; ingest DTOs/validation/mapper → gateway.
- [ ] **Step 2:** Delete the `mini-wsa` module and remove it from root `pom.xml`.
- [ ] **Step 3:** `mvn -q clean verify` green across the new reactor.
- [ ] **Step 4:** Commit — `refactor: retire mini-wsa monolith, relocate code to services`.

---

## After this phase
Real per-service logic is built by the re-mapped plans:
- **gateway** ← Part 1 (ingestion/validation/mapping) + publish-instead-of-store
- **enrichment** ← Part 2 (classification, scoring, Redis offender window) + consume/publish
- **event-store** ← Part 3/4 storage side (Mongo persistence, `EventStore` adapters, Postgres load-test)
- **analytics** ← Part 3 (stats) + Part 4 (samples) read side over the Mongo replica
- **event-generator** ← Part 5 (repoint feed to gateway; optional direct Kafka producer)
- **bonuses** ← time-series/rate-limit in analytics, alerting (new), Kafka streaming (now native)

## Self-Review
- Covers the SDD v2 module layout, contracts, infra, and a proven end-to-end seam before real logic — matches "in-memory first, harden later" (SDD §1, §14 risk mitigation).
- No placeholders in intent; the repetitive module scaffold uses an explicit template + per-service dependency table (not "similar to above").
- Cross-plan hand-off to the re-mapped per-service plans is explicit.
