# analytics — the read-only query surface over the security event store

The last hop of the Mini WSA pipeline. It reads the shared event store
**read-only** and serves two things a SOC dashboard would ask for: aggregate
threat statistics and filtered, paginated event samples. It does **not** consume
Kafka and it never writes — event-store is the sole writer.

Pipeline position: `enrichment → event-store → shared store (Postgres) → `**`analytics`**` read APIs`.

## Responsibilities

- Expose `GET /v1/stats/summary` — totals, per-category / per-action breakdowns, top attackers and top targeted paths for an optional config + time window.
- Expose `GET /v1/events/samples` — the raw enriched events behind the numbers, filtered and paginated, newest first.
- Aggregate identically across two interchangeable read adapters (in-memory, Postgres) behind one `AnalyticsReadStore` port — same grouping, sorting, and top-N (capped at 10) semantics regardless of store.
- Run **fully standalone** in the default `inmemory` mode with seeded demo data, so the read surface can be demoed with zero external dependencies.

## Entry points

Base path `/v1`, JSON responses, port **8084**.

| Method | Path | Query params | Purpose |
|--------|------|--------------|---------|
| GET | `/v1/stats/summary` | `configId`, `from`, `to` (all optional) | Aggregate statistics for the window |
| GET | `/v1/events/samples` | `configId`, `from`, `to`, `category`, `action`, `limit`, `offset` (all optional) | Paginated raw event samples |
| GET | `/v1/ping` | — | `{"status":"ok","service":"analytics"}` |
| GET | `/actuator/health` | — | Liveness / readiness |

`from` / `to` are ISO-8601 date-times (e.g. `2026-07-06T00:00:00Z`). `category`
is an `AttackCategory` name (INJECTION, XSS, PROTOCOL_VIOLATION, DATA_LEAKAGE,
BOT, DOS, RATE_LIMIT); `action` is an `Action` name (DENY, ALERT, MONITOR).
`limit` defaults to **20** and is clamped to **1..100**; `offset` defaults to
**0**. Every param is optional — with none supplied you get the whole store.

```bash
# aggregate stats for one config within a window
curl -s "localhost:8084/v1/stats/summary?configId=14227&from=2026-07-06T00:00:00Z&to=2026-07-07T00:00:00Z"

# newest INJECTION events that were denied, 5 at a time
curl -s "localhost:8084/v1/events/samples?category=INJECTION&action=DENY&limit=5&offset=0"
```

**`/stats/summary` response** — `configId`, `timeRange {from,to}`, `totalEvents`,
`byCategory` (map of category → `{count, avgThreatScore}`), `byAction` (map of
action → count), `topAttackers` (`[{clientIp, count, avgThreatScore}]`) and
`topTargetedPaths` (`[{path, count}]`). Top lists are capped at 10 and
`avgThreatScore` is rounded to **1 decimal place**.

**`/events/samples` response** — `total` (full match count, not the page size),
`limit`, `offset`, and `results` (the enriched event objects with nested `rule`
and `geoLocation`). Results are sorted by `timestamp` **descending**.

See the [root README](../../README.md#api-reference) for the full JSON bodies.

Invalid params (e.g. a bad `category` name or malformed `from`) return
`400 Bad Request` with the shape:

```json
{ "error": "Bad Request", "timestamp": "2026-07-06T09:00:00Z", "details": ["Invalid value 'FOO' for parameter 'category'"] }
```

## Configuration

Storage is selected at runtime via the single `wsa.storage` switch; the same
aggregation logic runs unchanged over each backend.

| Property / env | Default | Notes |
|----------------|---------|-------|
| `wsa.storage` | `inmemory` | `inmemory` \| `postgres` |
| `SPRING_DATASOURCE_URL` | — | JDBC URL (only when `wsa.storage=postgres`); pair with `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` |

In the default `inmemory` mode the store is populated with 15 seeded demo events
via `DevDataSeed` — the service is fully self-contained and needs nothing but the
JVM, which makes it ideal for a quick standalone demo. In `postgres`
mode it reads the **same** `events` table that event-store writes
(Postgres auto-config is filtered out unless the matching mode is active).

## Run standalone

Default mode needs **zero external dependencies** — no Kafka, no database:

```bash
mvn -pl services/analytics spring-boot:run
# then, against the seeded data:
curl -s "localhost:8084/v1/stats/summary?configId=14227"
curl -s "localhost:8084/v1/events/samples?limit=5"
```

Point it at a real shared store instead:

```bash
# PostgreSQL
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/wsa" \
SPRING_DATASOURCE_USERNAME=wsa SPRING_DATASOURCE_PASSWORD=wsa \
  mvn -pl services/analytics spring-boot:run -Dspring-boot.run.arguments="--wsa.storage=postgres"
```

## Build & test

```bash
mvn -pl services/analytics -am test
```

Unit tests (controllers, in-memory store) run with no infrastructure. The
Postgres adapter test is a **Testcontainers** integration test and requires
**Docker** to be running.

## Internal layout

Hexagonal — pure domain, one port, swappable adapters:

```
interfaces/rest/       StatsController, SamplesController, PingController,
                       response DTOs, ApiExceptionHandler (400 mapping)
application/           SummarizeStatistics / FetchEventSamples use cases
domain/model           EnrichedEventView (the read view)
domain/port            AnalyticsReadStore (the query port)
domain/query           StatisticsQuery/Summary, SampleQuery, EventSamplesPage,
                       Category/Attacker/Path statistics, TimeRange
infrastructure/persistence/inmemory   InMemoryAnalyticsReadStore (seeded)
infrastructure/persistence/postgres   PostgresAnalyticsReadStore
infrastructure/config  ReadStoreConfiguration (the two @Beans) + Postgres
                       auto-config import filter
infrastructure/seed    DevDataSeed (demo data for inmemory mode)
```

Both adapters implement the same `summarize` / `findSamples` contract with
identical aggregation semantics (group + count + average, top-10 ordered by
count then key, samples by `timestamp` DESC) — only the query mechanism differs.

## Contracts

The shared store schema this service reads lives in
[`../contracts`](../contracts) (the `events` Postgres table). Analytics is a
strict **READ-ONLY** consumer of that
schema: event-store owns writes and index creation, analytics only queries. When
the contract moves, re-check this service against it.
