# Mini WSA

Mini WSA ingests security events, classifies and scores them, and exposes
statistics and individual samples. The backend runs as one Java 21 Spring Boot
application with PostgreSQL. A separate Python script generates sample traffic.

## Run it

Run `docker compose up --build` with Docker Compose. The API listens on
`http://localhost:8080`. Compose creates a PostgreSQL volume for the event data.
Use `docker compose down` to stop the stack and preserve the data.
If port 8080 is in use, run `WSA_PORT=8088 docker compose up --build` and
use port 8088 in the examples.

To run with a PostgreSQL instance you already have, supply
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD`, then run `mvn spring-boot:run`. The application
creates its tables and indexes on startup.

Run `mvn clean verify` with JDK 21, Maven, and Docker. The suite exercises the
scoring matrix and runs the API against PostgreSQL in Testcontainers.

## Generate data

Python 3.10 or later is enough to feed 10,000 events into the API.

```bash
python3 scripts/generate_events.py --count 10000 --waves 5 \
  --post http://localhost:8080/v1/events/ingest
```

Use `--output events.json` to save a JSON array, or omit both output flags for
stdout. Each wave repeats an IP and path across a consecutive group of events.
Set `--seed` and `--start` for reproducible output. Change the seed when adding a
new dataset to the same database, because each seed produces the same event IDs.

## API

| Method and path | Behavior |
| --- | --- |
| `POST /v1/events/ingest` | Accepts a single DLR or an array. Returns `201` with `acceptedCount`. Invalid input returns `400` with field details and writes nothing. |
| `GET /v1/stats/summary` | Returns total events, counts and average scores by category, counts by action, plus the top 10 IPs and paths. Filters by `configId`, `from`, and `to`. |
| `GET /v1/events/samples` | Returns `total`, `limit`, `offset`, and `results` ordered by event timestamp descending. Supports the same filters plus `category` and `action`. The default limit is 20 and the maximum is 100. |
| `GET /v1/stats/timeseries` | Groups events into UTC buckets at `interval=1m`, `5m`, or `1h` using the summary filters. |
| `POST /v1/alerts/define` | Saves a rule with `category`, `threshold`, and `windowMinutes`. |
| `GET /v1/alerts/evaluate` | Evaluates each rule against event timestamps within its rolling window. A rule fires when its count exceeds its threshold. |

Query filters are optional. Time filters accept ISO-8601 timestamps and include
both bounds. The stats and samples APIs share a budget of 100 requests per IP
per minute by default. Excess requests return `429` with `Retry-After`. Set
`WSA_RATE_LIMIT_REQUESTS_PER_MINUTE` to adjust the budget.

```bash
curl -sS -X POST http://localhost:8080/v1/events/ingest \
  -H 'Content-Type: application/json' -d '{
    "eventId":"evt-example","timestamp":"2026-11-20T09:00:00Z","configId":14227,
    "policyId":"policy-14227","clientIp":"203.0.113.42","hostname":"shop.example.com",
    "path":"/api/v1/login","method":"POST","statusCode":403,"userAgent":"Mozilla/5.0",
    "rule":{"id":"r1","name":"SQL injection","message":"Synthetic event",
            "severity":"CRITICAL","category":"INJECTION"},
    "action":"DENY","geoLocation":{"country":"US","city":"New York"},
    "requestSize":256,"responseSize":512
  }'

curl -sS 'http://localhost:8080/v1/stats/summary?configId=14227'
curl -sS 'http://localhost:8080/v1/events/samples?category=INJECTION&action=DENY&limit=5'
curl -sS 'http://localhost:8080/v1/stats/timeseries?configId=14227&interval=5m'
curl -sS -X POST http://localhost:8080/v1/alerts/define \
  -H 'Content-Type: application/json' \
  -d '{"category":"INJECTION","threshold":3,"windowMinutes":10}'
curl -sS http://localhost:8080/v1/alerts/evaluate
```

## Design

```text
Generator / API client
        |
    Spring Boot
    |-- Ingestion: validate, classify, score, persist
    |-- Analytics: summary, samples, time-series, alerts
        |
    PostgreSQL
```

Each successful request commits before returning `201`, so its events are
available immediately to queries. The application assigns `receivedAt` using
its clock and stores the original DLR fields plus `attackType`, `threatScore`,
and `receivedAt`. Repeated event IDs keep the first stored record.

The score adds severity points (40, 30, 20, 10 for critical through low),
action points (20 for deny, 10 for alert, 0 for monitor), 15 for a path containing
`/admin` or `/login`, and 15 once the current event makes more than five events
from the same IP within ten minutes. The result is capped at 100.

PostgreSQL gives the write path one transaction and lets analytics filter,
aggregate, and paginate in SQL. Indexed columns support the time and config
queries and the per-IP offender lookup. JSONB holds the enriched payload
returned by samples. The older distributed implementation uses Kafka and Redis and is preserved on
the `microservices` branch.

### What was challenging

Two overlapping batches can both see the same count for one IP. Ingestion
takes transaction-scoped PostgreSQL locks for the affected event IDs and IPs
in a consistent order, so retries and overlapping batches keep the scoring
window consistent. The integration tests cover the concurrent sixth-event
boundary as well as the single-batch case.

### What I’d improve with more time

I’d profile the per-event SQL round trips and batch them where it pays off.
I’d use the measured ingestion and query latencies to decide when to split the
write path or move the offender window out of PostgreSQL.

## Codex

Open the repository root and select `$wsa-deploy`, followed by
`$wsa-walkthrough`. The terminal commands are in
[the deploy guide](docs/workflows/deploy.md) and
[the walkthrough](docs/workflows/walkthrough.md).
