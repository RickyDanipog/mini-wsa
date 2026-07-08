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
- Compute the deterministic `threatScore` (0–100) from a data-driven rule engine.
- Maintain a per-IP sliding window and flag repeat offenders.
- Publish `MessageEnvelope<EnrichedEventMessage>` to `events.enriched`, keyed by `configId`.
- No business REST API — only a health/ping surface.

## Threat scoring

Scoring is additive and capped at 100, but it is no longer hand-written Java
classes. It runs on a **subject-agnostic rule engine** (`ruleengine`:
`RuleOperator`, `RuleCondition(factKey, operator, operand)`, a generic
`Rule<T>` carrying a `type` discriminator, and a stateful `RuleEngine` you load
rules into and evaluate — matching lives on `RuleOperator`/`RuleCondition`).
Scoring is just one **usage** of that engine — rules of `type = "SCORING"`
whose `output` is the points value. `EnrichmentService` builds a typed
`ScoringFacts` (`severity, action, category, path, method, statusCode, clientIp,
offenderEventCount`) — it implements the engine's agnostic `Facts` interface —
and `RuleEngine` evaluates each enabled rule's
`(fact_key, operator, operand)` against it, and `RuleEngineThreatScoreCalculator`
sums the matched rules' points (clamped to 100). The engine is generic, so the
same machinery can drive other rule `type`s later.

The default rules reproduce the exact matrix below:

| Component | Contribution |
|-----------|--------------|
| Severity | `CRITICAL` +40 · `HIGH` +30 · `MEDIUM` +20 · `LOW` +10 |
| Action | `DENY` +20 · `ALERT` +10 · `MONITOR` +0 |
| Sensitive path | path contains `/admin` or `/login` → +15 |
| Repeat offender | more than 5 events from the same `clientIp` in the last 10 minutes → +15 |
| Cap | total clamped to `100` |

### Editing rules

Rules are **rows, not code**. They live in a shared `rules` table
(`id, type, title, fact_key, operator, operand, output, priority, enabled`), so
a reviewer adds, edits, or disables a scoring rule with a single SQL row — no
code change, no rebuild. Scoring rows carry `type = 'SCORING'` and put the
points in `output`. Adding a "bot category is worth 25 points" rule is one
insert:

```sql
INSERT INTO rules (id, type, title, fact_key, operator, operand, output, priority, enabled)
VALUES ('category-bot', 'SCORING', 'Bot category', 'category', 'EQUAL_TO', 'BOT', '25', 50, true);
```

Rule storage is chosen by `wsa.rules`: **`inmemory`** (default) serves the 8
built-in default rules above; **`postgres`** creates and seeds the `rules`
table with those same 8 defaults and then reads them (and any rows you add)
from Postgres. Postgres JDBC auto-configuration is gated by an
`AutoConfigurationImportFilter`, so an `inmemory` run opens no DB connection.

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
| `wsa.storage` (`WSA_STORAGE`) | `inmemory` | offender window + dedup store: `inmemory` or `redis` |
| `spring.data.redis.host` (`SPRING_DATA_REDIS_HOST`) | `localhost` | only read when `wsa.storage=redis` |
| `spring.data.redis.port` (`SPRING_DATA_REDIS_PORT`) | `6379` | only read when `wsa.storage=redis` |
| `wsa.rules` (`WSA_RULES`) | `inmemory` | scoring-rule store: `inmemory` (built-in defaults) or `postgres` (`rules` table) |
| `spring.datasource.url` (`SPRING_DATASOURCE_URL`) | — | only read when `wsa.rules=postgres`, e.g. `jdbc:postgresql://postgres:5432/wsa` |

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
ruleengine/       RuleOperator, RuleCondition, Rule<T> (type-tagged),
                  RuleEngine (stateful; subject-agnostic)
domain/
  model/          AttackType, ThreatScore (capped value object)
  port/           OffenderWindow, ProcessedEventLog, ScoringRuleRepository (driven ports)
  service/        RuleEngineThreatScoreCalculator, DefaultAttackTypeClassifier
application/      EnrichmentService (orchestrates dedup → window → score → classify)
infrastructure/
  config/         EnrichmentConfiguration (bean wiring), RuleEngineConfiguration,
                  RedisStorageConfiguration, RulesStorageAutoConfigurationFilter
  rules/          DefaultScoringRules (the 8 defaults), InMemoryScoringRuleRepository,
                  PostgresScoringRuleRepository (creates + seeds the rules table)
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
