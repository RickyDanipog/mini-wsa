# Gateway — Service Context

**Role:** the write-path entry point of Mini WSA. It accepts security events over
REST, validates them, transforms them to the shared contract, and publishes them
to Kafka. **It stores nothing.**

- **Port:** 8081 · **Base package:** `com.akamai.wsa.gateway`
- **Inbound:** `POST /v1/events/ingest` (single object or array/batch)
- **Outbound:** publishes `MessageEnvelope<RawEventMessage>` to topic `events.raw`,
  keyed by `clientIp`
- **Owns:** request validation, DTO→contract transform, correlationId minting.
  It holds no state and touches no database — the system's stores (PostgreSQL for
  the event store, Redis for the offender window) live in downstream services.

This is the front door of the pipeline
(event-generator / clients → **gateway** → `events.raw` → enrichment). For the
runnable entry points (endpoints, curl examples, config, how to run) see
[`README.md`](./README.md); this file is the "how it works / why" context.

## How it works

A request lands on `IngestController` as a raw `JsonNode`, so a single object and
an array both parse through the same path (`readRequests`). Each element binds to
an `IngestEventRequest` DTO.

- **All-or-nothing** batch validation: every item is validated with Bean
  Validation; a `201 {acceptedCount}` is returned only if all pass, otherwise a
  `400 {error:{code,message,details[]}}` naming the offending fields (indexed by
  batch position, e.g. `[0].clientIp`). There is no `{success,data}` envelope.
- Validation covers required fields, enum binding (`AttackCategory` / `Action` /
  `Severity`), and ISO-8601 timestamps.
- A `correlationId` is resolved from the `x-correlation-id` header, or a fresh
  UUID is minted when the header is absent or blank.
- Each valid DTO is mapped by `EventRequestMapper` to a contracts
  `RawEventMessage`, wrapped via `MessageEnvelope.of(correlationId, occurredAt,
  payload)`, and published to `events.raw` keyed by `clientIp`.
- `occurredAt` is stamped from an injected `Clock` and records gateway
  accept-time. The gateway does **not** stamp `receivedAt` — enrichment does that
  downstream.

## Key files

Hexagonal packages under `com.akamai.wsa.gateway`:

- `interfaces/rest` — `IngestController`, `PingController`, request DTOs, and the
  `GatewayExceptionHandler` that renders the `error` bodies.
- `application` — `EventRequestMapper` (DTO → contracts `RawEventMessage`).
- `infrastructure` — `messaging/RawEventPublisher` (Kafka adapter, JSON string
  key + value) and `config/GatewayConfiguration` (`Clock` + mapper beans).

There is no `domain` package: the gateway carries no business rules of its own —
it validates, maps to the shared contract, and publishes.

## Conventions

Java 21, Spring Boot 3.3.5, hexagonal layout, records, immutability, full
parameter names, and single-line logging tagged with `correlationId`.

## Shared contracts

This service produces `events.raw`. The authoritative message API is
[`services/contracts/ABOUT.md`](../contracts/ABOUT.md): `MessageEnvelope` uses an
`int version` with the 3-arg `MessageEnvelope.of(...)`, and
`RawEventMessage.clientIp` is a `String`. Every contract change is logged in
[`services/contracts/CHANGELOG.md`](../contracts/CHANGELOG.md) — the `events.raw`
(de)serialization must stay paired with the enrichment consumer.
