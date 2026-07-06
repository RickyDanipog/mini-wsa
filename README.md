# Mini WSA — Mini Security Analytics Pipeline

A backend service that ingests security event logs (DLRs), classifies and
enriches them, computes threat scores, stores them, and exposes analytics APIs.
Akamai CSI backend take-home.

> 🚧 **Status: environment prep / design phase.** Implementation has not started.
> This README is a stub and will be filled in as parts land.

## Documentation

| Doc | Purpose |
|-----|---------|
| [`docs/01-requirements.md`](docs/01-requirements.md) | Problem statement, environment & software requirements |
| [`docs/02-effort-estimate.md`](docs/02-effort-estimate.md) | Per-requirement effort breakdown |
| [`docs/03-sdd.md`](docs/03-sdd.md) | System Design Document — architecture, data flow, storage decision |
| [`AGENTS.md`](AGENTS.md) | Engineering guidelines & conventions |

## Tech stack

Java 21 · Spring Boot 3.x · Maven (multi-module) · Docker Compose · storage TBD.

## Build & run

Prerequisites: JDK 21, Maven, Docker.

```bash
# build + run all tests
mvn -q clean verify

# run the service locally (http://localhost:8080)
mvn -q -pl services/mini-wsa spring-boot:run
curl -s localhost:8080/v1/ping          # {"status":"ok"}

# full stack via Docker
docker compose up --build
```

## Architecture

Hexagonal / DDD: a storage-agnostic domain core behind a repository port, with
Spring and the storage engine as adapters. See [`docs/03-sdd.md`](docs/03-sdd.md).
