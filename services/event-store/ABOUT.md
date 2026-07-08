# event-store — Service Context

## What it is

The enriched-event **system of record** for Mini WSA (port **8083**). It runs as a
Kafka consumer: it reads `events.enriched`, maps the contract wire shape to its
own `StoredEvent` domain model, and persists each record. It exposes no business
REST API — the only HTTP surface is `GET /v1/ping` and Actuator health/info.

event-store is the pipeline's write side:

```
enrichment → events.enriched → event-store → shared store → analytics (read-only)
```

It is the **sole writer** of the shared store and the **owner** of that store's
schema and indexes. Analytics reads the same store read-only (a stand-in for a
read replica) and never writes to it.

## How it persists

- The listener (`EnrichedEventListener`) consumes
  `MessageEnvelope<EnrichedEventMessage>` from `events.enriched` (key = `configId`,
  consumer group `event-store`, `auto-offset-reset=earliest`). Values deserialize
  through a JSON deserializer wrapped in an `ErrorHandlingDeserializer`.
- `EnrichedEventMapper` flattens the nested wire shape — original request fields
  live under `EnrichedEventMessage.rawEvent()`, alongside the top-level
  `attackType` (String), `threatScore` (int), and `receivedAt` — into `StoredEvent`.
- Writes are **idempotent, keyed by `eventId`** (first-write-wins). Kafka delivery
  is at-least-once, so replays and redelivery must never create duplicates.

## Storage selector

All persistence sits behind the `EventStore` port (`saveAll`, `countAll`,
`findByConfigId`), so swapping the backend never touches the domain, mapper, or
listener. The backend is chosen at runtime by `wsa.storage` (env `WSA_STORAGE`):

| `wsa.storage` | Adapter | Behaviour |
|---------------|---------|-----------|
| `inmemory` (default) | `InMemoryEventStore` | `ConcurrentHashMap` keyed by `eventId`, `putIfAbsent`. No external deps — used for dev and tests. |
| `postgres` | `PostgresEventStore` | JDBC batch insert into the shared `events` table with `INSERT ... ON CONFLICT (event_id) DO NOTHING`. Creates the table and its indexes on startup (`@PostConstruct`). |

PostgreSQL is the shipped store for real runs. Indexes created:
`(config_id, timestamp desc)`, `(client_ip, timestamp desc)`, `(timestamp desc)`.

The Postgres datasource auto-configuration is gated by an
`AutoConfigurationImportFilter` (`PostgresStorageAutoConfigurationFilter`,
registered in `META-INF/spring.factories`). When `wsa.storage` is not `postgres`,
the JDBC auto-configs are filtered out and the service opens **no** DB connection.

> MongoDB was evaluated as a candidate and benchmarked against Postgres, then
> removed; it now lives on the `candidate/mongo-store` branch. It is not a current
> adapter.

## How it fits the pipeline

- Upstream: enrichment publishes `events.enriched`.
- Downstream: analytics reads the shared store directly (read-only).
- event-store owns schema/index creation and is the single writer, so the store's
  shape is a cross-service read contract with analytics — keep it stable and
  coordinate any change through the contracts module.

## Shared contracts

The Kafka message shapes and the shared store schema are defined in the contracts
module — see **[`services/contracts/ABOUT.md`](../contracts/ABOUT.md)**. Two facts
this service depends on:

- `EnrichedEventMessage` is **nested** — original fields under `.rawEvent()`;
  `attackType` is a `String`, `threatScore` an `int`.
- `MessageEnvelope<T>` deserialization needs the **parametric** target type
  (`MessageEnvelope<EnrichedEventMessage>`), not a raw class.

Contract changes are logged in **[`services/contracts/CHANGELOG.md`](../contracts/CHANGELOG.md)**;
any entry touching `EnrichedEventMessage`, `MessageEnvelope`, or the `events` table
affects the mapper, the consumer deserializer, or the Postgres DDL here.

## Key files

Hexagonal, base package `com.akamai.wsa.eventstore`:

```
domain/
  model/            StoredEvent, StoredRule, StoredGeoLocation
  port/             EventStore (saveAll, countAll, findByConfigId)
application/        EnrichedEventMapper (EnrichedEventMessage → StoredEvent)
interfaces/
  messaging/        EnrichedEventListener (@KafkaListener on events.enriched)
  rest/             PingController (/v1/ping)
infrastructure/
  config/           KafkaConsumerConfig, PostgresStorageAutoConfigurationFilter
  persistence/
    inmemory/       InMemoryEventStore
    postgres/       PostgresEventStore
```

Ports, run commands, config properties, and the test setup are in the
[README](./README.md).
