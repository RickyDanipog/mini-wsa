# Mini WSA — Mini Security Analytics Pipeline

An event-driven backend that ingests security event logs (DLRs), classifies and
enriches them, computes threat scores, stores them, and exposes analytics APIs.
Akamai CSI backend take-home.

> 🚧 **Status: Phase 0 (walking skeleton) complete.** A thin end-to-end pipeline
> runs over Kafka; per-service business logic is implemented next from each
> service's plan. Storage is in-memory for now (Mongo/Redis come later).

## Architecture (v2 — distributed services)

Five services connected by Kafka; each is hexagonal internally.

```
 event-generator ─REST─▶ gateway ─events.raw─▶ enrichment ─events.enriched─▶ event-store
   (test tool)           :8081                  :8082                          :8083 (in-memory)
                         validate,              classify + score                 │
                         publish                (in-memory offender window)      ▼
                                                                            analytics :8084
                                                                            (reads store; /stats, /samples)
```

See [`docs/03-sdd.md`](docs/03-sdd.md). Per-service plans + context live in each
`services/<svc>/.claude/`; the shared message API is `services/contracts/`.

## Documentation

| Doc | Purpose |
|-----|---------|
| [`docs/01-requirements.md`](docs/01-requirements.md) | Problem statement, environment & software requirements |
| [`docs/02-effort-estimate.md`](docs/02-effort-estimate.md) | Per-requirement effort breakdown |
| [`docs/03-sdd.md`](docs/03-sdd.md) | System Design Document (v2 — distributed) |
| [`AGENTS.md`](AGENTS.md) | Engineering guidelines & conventions |
| `services/contracts/.claude/context.md` | Shared Kafka message API (source of truth) |

## Tech stack

Java 21 · Spring Boot 3.3 · Maven (multi-module) · Apache Kafka · Docker Compose ·
storage in-memory now, Mongo + Redis later (PostgreSQL as a load-test baseline).

## Build & run

Prerequisites: JDK 21, Maven, Docker.

```bash
# build + run all tests (includes EmbeddedKafka pipeline tests)
mvn -q clean verify

# run a single service locally, e.g. the gateway on :8081
mvn -q -pl services/gateway spring-boot:run
curl -s localhost:8081/v1/ping          # {"status":"ok","service":"gateway"}

# full pipeline via Docker (Kafka + gateway + enrichment + event-store + analytics)
docker compose up --build
```

Modules: `contracts` (shared schemas) · `gateway` (:8081) · `enrichment` (:8082) ·
`event-store` (:8083) · `analytics` (:8084) · `event-generator` (CLI, feeds the gateway).
