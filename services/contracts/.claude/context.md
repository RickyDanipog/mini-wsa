# Contracts — Shared Context (authoritative API)

> **This is the single source of truth for the cross-service message API.**
> Every service that produces or consumes Kafka messages depends on these types.
> Before implementing any producer/consumer, read this file. **When any type here
> changes, log it in `CHANGELOG.md` (same folder) — that log is how every service
> agent is notified a contract moved.**

Module: `services/contracts` · base package `com.akamai.wsa.contracts` · plain jar (no Spring runtime).

## Types (as actually committed)

### `MessageEnvelope<T>` — wraps every Kafka message
```java
record MessageEnvelope<T>(String correlationId, Instant occurredAt, int version, T payload)
```
- `version` is an **`int`** (`MessageEnvelope.CURRENT_VERSION == 1`) — **not** a String.
- Construct via the factory: `MessageEnvelope.of(correlationId, occurredAt, payload)` — **3 args**; it sets `version = CURRENT_VERSION`.
- Generic: deserialize with a parametric type, e.g. Jackson `new TypeReference<MessageEnvelope<RawEventMessage>>(){}` (a bare `readValue(json, MessageEnvelope.class)` loses `T`).

### `RawEventMessage` — payload on topic `events.raw`
```java
record RawEventMessage(String eventId, Instant timestamp, int configId, String policyId,
    String clientIp, String hostname, String path, String method, int statusCode,
    String userAgent, RuleMessage rule, Action action, GeoLocationMessage geoLocation,
    long requestSize, long responseSize)
```
`clientIp` is a **`String`** on the wire (not a value object).

### `EnrichedEventMessage` — payload on topic `events.enriched`  ⚠️ NESTED
```java
record EnrichedEventMessage(RawEventMessage rawEvent, String attackType, int threatScore, Instant receivedAt)
```
- The original DLR fields live **under `.rawEvent()`** — e.g. `msg.rawEvent().configId()`, `msg.rawEvent().clientIp()`. There is **no** flat `configId()`/`eventId()` accessor and **no** `EnrichedEventMessage.from(...)` factory — build it with the canonical constructor.
- `attackType` is a **`String`** (the display name, e.g. `"SQL/Command Injection"`). There is **no `contracts.AttackType` enum** — the display name is produced by the enrichment service and carried as text.
- `threatScore` is a plain **`int`** (0–100).

### `RuleMessage` / `GeoLocationMessage`
```java
record RuleMessage(String id, String name, String message, Severity severity, AttackCategory category)
record GeoLocationMessage(String country, String city)
```

### Enums (shared vocabulary)
- `AttackCategory` = INJECTION, XSS, PROTOCOL_VIOLATION, DATA_LEAKAGE, BOT, DOS, RATE_LIMIT
- `Action` = DENY, ALERT, MONITOR
- `Severity` = CRITICAL, HIGH, MEDIUM, LOW

Enums serialize to their **names** (matches the assignment's `"INJECTION"`, `"DENY"`, `"CRITICAL"`).

## Topics
| Topic | Key | Value | Producer → Consumer |
|-------|-----|-------|---------------------|
| `events.raw` | `clientIp` | `MessageEnvelope<RawEventMessage>` | gateway → enrichment |
| `events.enriched` | `configId` | `MessageEnvelope<EnrichedEventMessage>` | enrichment → event-store |

Serialization: **JSON** for now (Avro + Schema Registry is a hardening step). Producers and consumers of the same topic must use a matching (de)serializer for the enveloped generic type.

## The change-notification rule
Contracts are shared context across all service agents. When you change any type
above: (1) update this file, (2) append an entry to `CHANGELOG.md` with the date,
what changed, and which services are affected, (3) each affected service's plan
must be re-checked against the new shape before it is executed.
