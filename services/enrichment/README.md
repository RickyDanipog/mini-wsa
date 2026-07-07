# enrichment — the scoring brain of the pipeline

Consumes raw security events, classifies each one, computes a deterministic
threat score, flags repeat-offender IPs, and republishes the enriched record.
It is stateless per event except for a small sliding window of recent activity
per client IP.

Pipeline position: gateway → `events.raw` → **enrichment** → `events.enriched` → event-store.

See the root [`../../README.md`](../../README.md) for the full architecture.

## Responsibilities

- Consume `MessageEnvelope<RawEventMessage>` from `events.raw` and deserialize the enveloped generic type.
- Deduplicate by `eventId` (first-sight wins) so a redelivered message is enriched at most once.
- Classify `rule.category` → a human-readable `attackType` display name.
- Compute the deterministic `threatScore` (0–100) from composable scoring rules.
- Maintain a per-IP sliding window and flag repeat offenders.
- Publish `MessageEnvelope<EnrichedEventMessage>` to `events.enriched`, keyed by `configId`.
- No business REST API — only a health/ping surface.

## Threat scoring

Scoring is additive and capped at 100. Each contribution is a standalone
`ScoringRule` (`SeverityRule`, `ActionRule`, `SensitivePathRule`,
`RepeatOffenderRule`) composed by `RuleBasedThreatScoreCalculator`, so the
matrix is unit-testable in isolation and easy to extend.

| Component | Contribution |
|-----------|--------------|
| Severity | `CRITICAL` +40 · `HIGH` +30 · `MEDIUM` +20 · `LOW` +10 |
| Action | `DENY` +20 · `ALERT` +10 · `MONITOR` +0 |
| Sensitive path | path contains `/admin` or `/login` → +15 |
| Repeat offender | more than 5 events from the same `clientIp` in the last 10 minutes → +15 |
| Cap | total clamped to `100` |

`attackType` classification (`rule.category` → display name), via `DefaultAttackTypeClassifier`:

| Category | attackType |
|----------|-----------|
| `INJECTION` | SQL/Command Injection |
| `XSS` | Cross-Site Scripting |
| `PROTOCOL_VIOLATION` | Protocol Anomaly |
| `DATA_LEAKAGE` | Data Exfiltration |
| `BOT` | Bot Activity |
| `DOS` | Denial of Service |
| `RATE_LIMIT` | Rate Limiting |

The repeat-offender bonus is the one **stateful** rule. Each event is recorded
then counted within the window at processing time (record-then-read: the 6th
event from an IP is the first to be flagged, since the threshold is `> 5`).
That window lives behind the `OffenderWindow` port — in-memory for dry runs, a
Redis per-IP sorted set under load.

## Entry points

Kafka (the only functional interface):

- **Consumes** `events.raw` (`MessageEnvelope<RawEventMessage>`, keyed by `clientIp` upstream), consumer group `enrichment`, `auto-offset-reset=earliest`.
- **Produces** `events.enriched` (`MessageEnvelope<EnrichedEventMessage>`, keyed by `configId`). The correlation id and `occurredAt` are carried through from the incoming envelope.

HTTP (operational only):

- `GET /actuator/health`, `/actuator/info` — Spring Boot Actuator.
- `GET /v1/ping` → `{"status":"ok","service":"enrichment"}`.

## Configuration

| Property (env) | Default | Notes |
|----------------|---------|-------|
| `server.port` | `8082` | HTTP port |
| `spring.kafka.bootstrap-servers` (`KAFKA_BOOTSTRAP_SERVERS`) | `localhost:9092` | Kafka brokers |
| `spring.kafka.consumer.group-id` | `enrichment` | consumer group |
| `wsa.topics.events-raw` | `events.raw` | inbound topic |
| `wsa.topics.events-enriched` | `events.enriched` | outbound topic |
| `wsa.storage` (`WSA_STORAGE`) | `inmemory` | `inmemory` or `redis` |
| `spring.data.redis.host` (`SPRING_DATA_REDIS_HOST`) | `localhost` | only read when `wsa.storage=redis` |
| `spring.data.redis.port` (`SPRING_DATA_REDIS_PORT`) | `6379` | only read when `wsa.storage=redis` |

Redis auto-configuration is excluded in `application.yml` and only wired up
(via `RedisStorageConfiguration`, gated by `@ConditionalOnProperty`) when
`wsa.storage=redis`, so an `inmemory` run opens no Redis connection.

The offender-window length (10 min), the repeat-offender threshold (5), and the
dedup retention are **not** externalized in `application.yml` — they are code
constants: the window and threshold live in `EnrichmentService`; the Redis
adapters hardcode a window-key TTL of window + 1 min (`RedisOffenderWindow`) and
a 1-hour dedup key TTL (`RedisProcessedEventLog`). The in-memory offender window
and dedup log are unbounded (fine for dry runs, evicted only by Redis under load).

## Run standalone

Needs a reachable Kafka broker. Defaults to in-memory storage (no Redis):

```bash
mvn -pl services/enrichment spring-boot:run
```

With Redis-backed window + dedup:

```bash
WSA_STORAGE=redis \
SPRING_DATA_REDIS_HOST=localhost \
SPRING_DATA_REDIS_PORT=6379 \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
mvn -pl services/enrichment spring-boot:run
```

## Build & test

```bash
mvn -pl services/enrichment -am test
```

Redis adapters are verified against a real engine via Testcontainers, so those
integration tests require a running Docker daemon.

## Internal layout

Hexagonal packages under `com.akamai.wsa.enrichment`:

```
domain/
  model/          AttackType, ThreatScore (capped value object)
  port/           OffenderWindow, ProcessedEventLog (driven ports)
  service/        ScoringRule + Severity/Action/SensitivePath/RepeatOffender rules,
                  RuleBasedThreatScoreCalculator, DefaultAttackTypeClassifier,
                  ThreatScoringInputs
application/      EnrichmentService (orchestrates dedup → window → score → classify)
infrastructure/
  config/         EnrichmentConfiguration (bean wiring), RedisStorageConfiguration
  window/         InMemoryOffenderWindow, RedisOffenderWindow (sorted set)
  dedup/          InMemoryProcessedEventLog, RedisProcessedEventLog (SETNX)
  messaging/      EnrichedEventPublisher
interfaces/
  messaging/      RawEventListener (@KafkaListener on events.raw)
  rest/           PingController
```

## Contracts

Message shapes come from [`../contracts`](../contracts) (the source of truth).
This service consumes `RawEventMessage` and produces `EnrichedEventMessage`.

Note the **nested** shape of `EnrichedEventMessage`:

```java
record EnrichedEventMessage(RawEventMessage rawEvent, String attackType, int threatScore, Instant receivedAt)
```

The original DLR fields live under `.rawEvent()` — e.g. `msg.rawEvent().configId()`,
`msg.rawEvent().clientIp()`. There is no flat accessor and no `from(...)` factory;
the publisher derives the Kafka key from `envelope.payload().rawEvent().configId()`.
`attackType` is the display name as text (there is no `AttackType` enum in contracts).
