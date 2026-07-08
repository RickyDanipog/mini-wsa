# gateway — the ingestion edge of the Mini WSA pipeline

Validates single or batched security events, stamps a correlation id, and
publishes them onto Kafka. It is the front door of the pipeline
(event-generator / clients → **gateway** → `events.raw` → enrichment) and holds
no state — it does not persist anything. See the root
[`README.md`](../../README.md) for the big picture.

## Responsibilities

- Accept a single event object or a batch (array) on `POST /v1/events/ingest`.
- Validate every event with Bean Validation; reject the whole request if any
  item is invalid (all-or-nothing).
- Resolve a correlation id from the optional `x-correlation-id` header, or mint a
  new UUID when the header is absent/blank.
- Map each `IngestEventRequest` to a contracts `RawEventMessage`, wrap it in a
  `MessageEnvelope`, and publish to `events.raw` keyed by `clientIp`.
- Expose liveness/health via Spring Boot Actuator.

## Entry points

### HTTP (port 8081)

| Method | Path | Purpose | Success |
|--------|------|---------|---------|
| `POST` | `/v1/events/ingest` | Ingest one event or a batch; optional `x-correlation-id` header | `201 Created` |
| `GET`  | `/v1/ping` | Liveness probe → `{"status":"ok","service":"gateway"}` | `200 OK` |
| `GET`  | `/actuator/health` | Spring Boot health (also `/actuator/info`) | `200 OK` |

```bash
curl -s -X POST localhost:8081/v1/events/ingest \
  -H 'content-type: application/json' \
  -H 'x-correlation-id: demo-001' \
  -d '{
    "eventId": "evt-0001", "timestamp": "2026-07-07T09:00:00Z",
    "configId": 14227, "policyId": "policy-14227",
    "clientIp": "203.0.113.42", "hostname": "shop.example.com",
    "path": "/api/v1/login", "method": "POST", "statusCode": 403,
    "userAgent": "Mozilla/5.0",
    "rule": { "id": "rule-INJECTION", "name": "SQLi signature",
              "message": "SQL injection detected", "severity": "CRITICAL",
              "category": "INJECTION" },
    "action": "DENY",
    "geoLocation": { "country": "US", "city": "New York" },
    "requestSize": 256, "responseSize": 512
  }'
```

`201` returns `{ "acceptedCount": 1 }`. On a `400`, the body carries an `error`
with `code` `VALIDATION_FAILED` (per-field `details`, indexed by batch position,
e.g. `[0].clientIp`) or `MALFORMED_REQUEST` (body could not be parsed). See the
root README's API reference for the full request schema and error shape.

### Kafka

Ingestion has two inbound adapters over one shared `IngestEvents` use case: the
REST endpoint above and a Kafka listener.

- **Inbound (listener):** `@KafkaListener` on topic **`events.ingest`**, consumer
  group **`gateway-ingest`**, consuming one event (object or batch array) per
  message as a JSON string. It runs the **same validation + mapping** as REST and
  republishes to `events.raw`. The correlation id comes from the
  `x-correlation-id` Kafka header when present, else a fresh UUID. Kafka has no
  synchronous response, so **invalid messages are logged (single-line WARN) and
  skipped** — there is no dead-letter topic.
- **Outbound (producer):** `MessageEnvelope<RawEventMessage>` to topic
  **`events.raw`**, keyed by **`clientIp`**, serialized as JSON (string key +
  string value).

#### Streaming ingestion (bonus B2)

`scripts/produce-to-kafka.sh` publishes generated events straight onto
`events.ingest`, exercising the streaming path end-to-end:

```bash
scripts/produce-to-kafka.sh [count=200] [topic=events.ingest]
```

It runs the event-generator in `JSON_FILE` mode and streams the result one JSON
object per message into the running `wsa-kafka` container's console producer.
Requires the stack up plus JDK 21 / Maven and `jq` on the host.

## Configuration

| Property (env) | Default | Purpose |
|----------------|---------|---------|
| `server.port` | `8081` | HTTP listen port |
| `spring.kafka.bootstrap-servers` (`KAFKA_BOOTSTRAP_SERVERS`) | `localhost:9092` | Kafka broker to publish to |
| `wsa.topics.events-raw` | `events.raw` | Destination topic for raw events |
| `wsa.topics.events-ingest` | `events.ingest` | Inbound streaming-ingestion topic (Kafka listener) |
| `spring.kafka.consumer.group-id` | `gateway-ingest` | Consumer group for the ingestion listener (`auto-offset-reset: earliest`) |
| `management.endpoints.web.exposure.include` | `health,info` | Exposed actuator endpoints |

No `wsa.storage` property here — the gateway keeps no store. Its only runtime
dependency is a reachable Kafka broker.

## Run standalone

Run from the repo root (needs a reachable Kafka broker):

```bash
docker compose up kafka           # bring up a broker
mvn -pl services/gateway spring-boot:run
```

Point it at another broker via env:

```bash
KAFKA_BOOTSTRAP_SERVERS=broker:9092 mvn -pl services/gateway spring-boot:run
```

## Build & test

```bash
mvn -pl services/gateway -am test
```

Tests cover the ingest controller (unit + Spring integration over an embedded
broker), the ping endpoint, and the request→message mapper.

## Internal layout

Hexagonal packages under `com.akamai.wsa.gateway`:

- `interfaces/rest` — `IngestController`, `PingController`, request DTOs, and the
  `GatewayExceptionHandler` that renders the `error` bodies.
- `application` — `EventRequestMapper` (DTO → contracts `RawEventMessage`).
- `infrastructure` — `messaging/RawEventPublisher` (Kafka adapter) and
  `config/GatewayConfiguration` (`Clock` + mapper beans).

There is no `domain` package: the gateway carries no business rules of its own —
it validates, maps to the shared contract, and publishes.

## Contracts

`RawEventMessage`, `MessageEnvelope`, and the shared enums come from
[`../contracts`](../contracts), the single source of truth for cross-service
message shapes.
</content>
</invoke>
