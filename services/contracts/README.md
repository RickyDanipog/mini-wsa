# contracts — shared message API & store schema (library)

A plain library jar (no Spring runtime — just Jackson) holding the records and
enums that define every cross-service shape in Mini WSA. It exists so producers
and consumers agree on one authoritative definition instead of drifting apart;
it is depended on by **gateway**, **enrichment**, **event-store**, **analytics**,
and **event-generator**. This is the one module under `services/` you do **not**
run standalone — there is nothing to start, only something to build against.

Base package `com.akamai.wsa.contracts`. The authoritative, always-current
version of everything below lives in [`ABOUT.md`](ABOUT.md);
this README is its public-facing summary.

## Message types

- **`MessageEnvelope<T>`** — wraps every Kafka message:
  `(String correlationId, Instant occurredAt, int version, T payload)`.
  `version` is an **`int`** (`MessageEnvelope.CURRENT_VERSION == 1`), not a String.
  Construct via the **3-arg** factory `MessageEnvelope.of(correlationId, occurredAt, payload)`,
  which stamps `version = CURRENT_VERSION`. Because it is generic, deserialize with
  a parametric type (Jackson `new TypeReference<MessageEnvelope<RawEventMessage>>(){}`) —
  a bare `readValue(json, MessageEnvelope.class)` loses `T`.

- **`RawEventMessage`** — payload on `events.raw`. Flat DLR fields:
  `eventId`, `timestamp`, `configId` (int), `policyId`, `clientIp` (a **`String`** on the wire),
  `hostname`, `path`, `method`, `statusCode` (int), `userAgent`, `rule` (`RuleMessage`),
  `action` (`Action`), `geoLocation` (`GeoLocationMessage`), `requestSize` (long),
  `responseSize` (long).

- **`EnrichedEventMessage`** — payload on `events.enriched`. ⚠️ **NESTED**:
  `(RawEventMessage rawEvent, String attackType, int threatScore, Instant receivedAt)`.
  The original DLR fields live **under `.rawEvent()`** (e.g. `msg.rawEvent().configId()`,
  `msg.rawEvent().clientIp()`) — there is no flat `configId()`/`eventId()` accessor and
  no `from(...)` factory; build it with the canonical constructor. `attackType` is a
  **`String`** display name (e.g. `"SQL/Command Injection"`) produced by enrichment —
  there is no `contracts.AttackType` enum. `threatScore` is a plain **`int`** (0–100).

- **`RuleMessage`** — `(String id, String name, String message, Severity severity, AttackCategory category)`.
- **`GeoLocationMessage`** — `(String country, String city)`.

## Enums

Shared vocabulary; each serializes to its **name** (e.g. `"INJECTION"`, `"DENY"`, `"CRITICAL"`).

- **`AttackCategory`** — `INJECTION`, `XSS`, `PROTOCOL_VIOLATION`, `DATA_LEAKAGE`, `BOT`, `DOS`, `RATE_LIMIT`
- **`Action`** — `DENY`, `ALERT`, `MONITOR`
- **`Severity`** — `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`

## Kafka topics

Serialization is **JSON** (Avro + Schema Registry is a future hardening step).
Producers and consumers of a topic must use a matching (de)serializer for the
enveloped generic type.

| Topic | Key | Value | Producer → Consumer |
|-------|-----|-------|---------------------|
| `events.raw` | `clientIp` | `MessageEnvelope<RawEventMessage>` | gateway → enrichment |
| `events.enriched` | `configId` | `MessageEnvelope<EnrichedEventMessage>` | enrichment → event-store |

## Shared store schema

When `wsa.storage=postgres`, **event-store WRITES** and **analytics READS** the
same `events` store (DB `wsa`) — locally one instance, a read replica in
production. This shape is a cross-service contract, same rule as the Kafka schemas.

- **Postgres** — table `events`, with `rule`/`geo` flattened to `rule_*`/`geo_*`
  columns; `event_id` primary key; `INSERT ... ON CONFLICT (event_id) DO NOTHING`
  (first-write-wins); **enum-name encoding**; `Instant` mapped to UTC `TIMESTAMPTZ`;
  indexes `(config_id, timestamp desc)`, `(client_ip, timestamp desc)`, `(timestamp desc)`.
- Event-store is the **sole writer**; analytics is **read-only**. Each service maps
  the shape with its own class (no shared Spring-Data type here — this stays a plain jar),
  but the table name, column names, and encoding MUST match exactly.
- MongoDB was an evaluated storage candidate (its collection contract lived here);
  Postgres won the benchmark, so the Mongo adapter now lives on the `candidate/mongo-store` branch.

Full field list and DDL: [`ABOUT.md`](ABOUT.md).

## Changing a contract

Contracts are shared context across every service. When you change any type here:

1. Update [`ABOUT.md`](ABOUT.md) (the source of truth).
2. Append a dated entry to [`CHANGELOG.md`](CHANGELOG.md) — what
   changed, which services are affected, required follow-up.
3. Re-check every affected service against the new shape before it ships.

The CHANGELOG is the broadcast channel: each service reads it before implementing,
so a contract move is seen by all.

## Build

It is a dependency, not a service — there is no run command. Other modules build
against it, so install it into the local repo first:

```bash
mvn -pl services/contracts install
```

Part of the Mini WSA reactor — see the [root README](../../README.md).
