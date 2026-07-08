# Enrichment Service

The scoring brain of Mini WSA. It consumes raw security events, classifies each
one, computes a deterministic threat score, flags repeat-offender IPs, and
republishes an enriched record. This is where the graded business logic lives.

**Runtime:** Spring Boot on port **8082**, base package `com.akamai.wsa.enrichment`.

- **Consumes** `events.raw` — `MessageEnvelope<RawEventMessage>` (keyed by `clientIp` upstream), consumer group `enrichment`.
- **Produces** `events.enriched` — `MessageEnvelope<EnrichedEventMessage>`, keyed by `configId`. The correlation id and `occurredAt` carry through from the incoming envelope.
- No business REST API — only `GET /actuator/health`, `/actuator/info`, and `GET /v1/ping`.

## Pipeline position

```
gateway → events.raw → enrichment → events.enriched → event-store (PostgreSQL)
```

## What it does per event

`EnrichmentService.enrich(...)` is the orchestrator:

1. **Deduplicate** by `eventId` (first-sight wins), so a redelivered message is enriched at most once.
2. **Record then read** the client IP against the sliding offender window and derive the repeat-offender flag.
3. **Score** — compute the 0–100 `threatScore` from the composable rules.
4. **Classify** — map `rule.category` to a human-readable `attackType` display name.
5. Emit an `EnrichedEventMessage`; dropped duplicates return `Optional.empty()`.

## Threat scoring

Scoring is additive and clamped to 100. Each contribution is a standalone
`ScoringRule`, composed by `RuleBasedThreatScoreCalculator`, so the matrix is
unit-testable in isolation and easy to extend.

| Component | Contribution |
|-----------|--------------|
| Severity | `CRITICAL` +40 · `HIGH` +30 · `MEDIUM` +20 · `LOW` +10 |
| Action | `DENY` +20 · `ALERT` +10 · `MONITOR` +0 |
| Sensitive path | path contains `/admin` or `/login` → +15 |
| Repeat offender | more than 5 events from the same `clientIp` in the last 10 min → +15 |
| Cap | total clamped to `100` (highest actually reachable is **90**) |

The scorer is a **pure function**: the repeat-offender flag is computed by the
caller and injected through `ThreatScoringInputs`, so no rule touches state.
`ThreatScore` is a value object that rejects out-of-range values and exposes
`ofCapped(...)`.

`DefaultAttackTypeClassifier` maps `rule.category` to a display name
(`INJECTION` → SQL/Command Injection, `XSS` → Cross-Site Scripting,
`PROTOCOL_VIOLATION` → Protocol Anomaly, `DATA_LEAKAGE` → Data Exfiltration,
`BOT` → Bot Activity, `DOS` → Denial of Service, `RATE_LIMIT` → Rate Limiting).

## The offender window

The repeat-offender bonus is the one stateful input. The window uses
**record-then-read** semantics against a sliding 10-minute window based on the
event's `receivedAt`: the current event is recorded first, then counted, and the
flag trips when `count > 5` — so the **6th** event from an IP is the first to be
flagged.

The window sits behind the `OffenderWindow` port, with two adapters:

- **in-memory** (default) — for dry runs; unbounded, cleared only on restart.
- **Redis** — a per-IP sorted set with a window-key TTL of window + 1 min, selected by `wsa.storage=redis`.

Redis auto-configuration is excluded unless `wsa.storage=redis`, so an
in-memory run opens no Redis connection. Dedup follows the same pattern via the
`ProcessedEventLog` port (in-memory vs. Redis `SETNX` with a 1-hour key TTL).

## Shared contracts

Message shapes come from the shared `contracts` module — see
[`../contracts/ABOUT.md`](../contracts/ABOUT.md) for the authoritative API, and
[`../contracts/CHANGELOG.md`](../contracts/CHANGELOG.md) for changes.

`EnrichedEventMessage` is **nested**: the original fields live under `.rawEvent()`.

```java
record EnrichedEventMessage(RawEventMessage rawEvent, String attackType, int threatScore, Instant receivedAt)
```

There is no flat accessor and no `from(...)` factory; the publisher derives the
Kafka key from `envelope.payload().rawEvent().configId()`. `attackType` is the
display name as text (there is no `AttackType` enum in contracts).

## Internal layout

Hexagonal packages under `com.akamai.wsa.enrichment`. `domain/` imports no
Spring/Kafka/Redis — pure classes wired to beans in
`infrastructure/config/EnrichmentConfiguration`.

```
domain/
  model/          AttackType, ThreatScore (capped value object)
  port/           OffenderWindow, ProcessedEventLog (driven ports)
  service/        ScoringRule + Severity/Action/SensitivePath/RepeatOffender rules,
                  RuleBasedThreatScoreCalculator, DefaultAttackTypeClassifier, ThreatScoringInputs
application/      EnrichmentService (dedup → window → score → classify)
infrastructure/
  config/         EnrichmentConfiguration (bean wiring), RedisStorageConfiguration
  window/         InMemoryOffenderWindow, RedisOffenderWindow (sorted set)
  dedup/          InMemoryProcessedEventLog, RedisProcessedEventLog (SETNX)
  messaging/      EnrichedEventPublisher
interfaces/
  messaging/      RawEventListener (@KafkaListener on events.raw)
  rest/           PingController
```

See the [`README.md`](./README.md) for config properties, run commands, and tests.
