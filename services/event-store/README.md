# event-store — the durable sink for enriched security events

Consumes enriched events off Kafka and persists the full record to the shared
store. It is the pipeline's write side: **enrichment → `events.enriched` →
event-store → shared store**, which [analytics](../analytics) then reads
read-only. event-store is the **sole writer** of that store and the owner of its
schema/indexes.

## Responsibilities

- Consume `MessageEnvelope<EnrichedEventMessage>` from `events.enriched`.
- Map the nested wire shape (`EnrichedEventMessage.rawEvent()` + `attackType`,
  `threatScore`, `receivedAt`) to the domain `StoredEvent`.
- Persist each record idempotently, keyed by `eventId`, so replays and
  at-least-once redelivery never create duplicates.
- Own creation of the store's schema/indexes for the DB backend (Postgres
  `CREATE TABLE/INDEX IF NOT EXISTS`) at startup.

There is **no business REST API** — the store is written from Kafka and read by
analytics directly against the shared collection/table. The only HTTP surface is
health/ping (below).

## Entry points

- **Kafka listener** (`EnrichedEventListener`): consumes topic `events.enriched`
  (key = `configId`), consumer group `event-store`, `auto-offset-reset=earliest`.
  Values are deserialized as `MessageEnvelope<EnrichedEventMessage>` via a
  JSON deserializer wrapped in an `ErrorHandlingDeserializer` (trusted package
  `com.akamai.wsa.contracts`).
- **HTTP** (port 8083): `GET /v1/ping` → `{"status":"ok","service":"event-store"}`;
  Actuator `GET /actuator/health` and `/actuator/info`.

## Storage

Selected at runtime by `wsa.storage` (env `WSA_STORAGE`). The matching DB
auto-configuration is gated by an `AutoConfigurationImportFilter`
(`PostgresStorageAutoConfigurationFilter`, registered in
`META-INF/spring.factories`), so a non-matching mode opens **no** connection to
that database.

| `wsa.storage` | Adapter | What it does |
|---------------|---------|--------------|
| `inmemory` (default) | `InMemoryEventStore` | `ConcurrentHashMap` keyed by `eventId`; `putIfAbsent` (first-write-wins). No external deps. |
| `postgres` | `PostgresEventStore` | JDBC batch insert into the shared `wsa.events` table with `INSERT ... ON CONFLICT (event_id) DO NOTHING` (first-write-wins). Runs the DDL on startup (`@PostConstruct`). |

event-store **owns** schema/index creation (Postgres DDL) and is the **sole
writer**; analytics reads the same store **read-only** (a stand-in for a read
replica). The shared table shape is a cross-service contract — see the "Shared
Postgres `events` table" section in
[`../contracts/ABOUT.md`](../contracts/ABOUT.md).
Indexes created: `{configId, timestamp desc}`, `{clientIp, timestamp desc}`,
`{timestamp desc}`.

## Configuration

| Property (env) | Default | Notes |
|----------------|---------|-------|
| `server.port` | `8083` | HTTP port |
| `spring.kafka.bootstrap-servers` (`KAFKA_BOOTSTRAP_SERVERS`) | `localhost:9092` | Kafka brokers |
| `wsa.topics.enriched` | `events.enriched` | source topic |
| `wsa.consumer-group` | `event-store` | consumer group id |
| `wsa.storage` (`WSA_STORAGE`) | `inmemory` | `inmemory` \| `postgres` |
| `SPRING_DATASOURCE_URL` | — | required when `wsa.storage=postgres` (e.g. `jdbc:postgresql://localhost:5432/wsa`) |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | — | Postgres credentials |

Runtime dependencies: **Kafka always**; **Postgres** only when
`wsa.storage=postgres`.

## Run standalone

Needs a reachable Kafka broker. Defaults to in-memory storage (no DB needed):

```bash
mvn -pl services/event-store spring-boot:run
```

Against Postgres:

```bash
WSA_STORAGE=postgres \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/wsa \
SPRING_DATASOURCE_USERNAME=wsa SPRING_DATASOURCE_PASSWORD=wsa \
mvn -pl services/event-store spring-boot:run
```

## Build & test

```bash
mvn -pl services/event-store -am test
```

The Postgres adapter integration test (`PostgresEventStoreIT`) and the
end-to-end `EnrichedEventListenerIntegrationTest` use **Testcontainers** and need
a running **Docker** daemon.

## Internal layout

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
  config/           KafkaConsumerConfig, Postgres AutoConfigurationImportFilter
  persistence/
    inmemory/       InMemoryEventStore
    postgres/       PostgresEventStore
```

## Contracts

Message and store shapes are defined in [`../contracts`](../contracts) (see
[`../contracts/ABOUT.md`](../contracts/ABOUT.md)). Two things
to keep in mind:

- **`EnrichedEventMessage` is nested** — the original event fields live under
  `.rawEvent()` (e.g. `msg.rawEvent().configId()`), alongside the top-level
  `attackType` (String display name), `threatScore` (int 0–100), `receivedAt`.
- The **shared store schema** (Postgres `events` table in database `wsa`) is a
  cross-service contract co-owned with analytics; event-store creates it,
  analytics reads it. Change it in contracts and log it in that module's
  `CHANGELOG.md`.
