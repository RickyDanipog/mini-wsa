# analytics — Service Context

## What it is

The **read side** of Mini WSA, on port **:8084**. It is a standalone,
read-only query surface over the shared security event store — it exposes
aggregate threat statistics and filtered event samples for a SOC-style
dashboard. It **does not consume Kafka and never writes**: `event-store` is the
sole writer, and analytics only queries what that service has already persisted.

Pipeline position: `enrichment → event-store → shared store → `**`analytics`**` read APIs`.

## The two read endpoints

- `GET /v1/stats/summary` — aggregation for an optional config + time window:
  `totalEvents`, `byCategory` (count + avgThreatScore), `byAction` counts,
  top-10 attackers, and top-10 targeted paths.
- `GET /v1/events/samples` — the raw enriched events behind those numbers:
  filtered by config / window / category / action, sorted `timestamp` DESC,
  paginated (`limit` default 20, clamped 1..100; `offset` default 0), with a
  `total` full-match count.

Every query parameter is optional; with none supplied you get the whole store.
Invalid values (bad enum name, malformed timestamp) map to `400 Bad Request`
via `ApiExceptionHandler`. Responses use the assignment's **exact JSON shapes**
(no `{success,data}` envelope). See the [README](README.md) for request/response
bodies and curl examples.

## The read-store adapters — and why they must agree

Storage sits behind the domain-owned `AnalyticsReadStore` port (`summarize` /
`findSamples`, plus `timeSeries` and `countByCategoryWithin` for the B3/B1
bonuses), selected at runtime by the single `wsa.storage` switch. Two adapters
implement it:

- **in-memory** (`wsa.storage=inmemory`, the default) — seeded with demo events
  by `DevDataSeed`, so the read surface runs with zero external dependencies.
- **PostgreSQL** (`wsa.storage=postgres`) — reads the **same** `events` table
  `event-store` writes, aggregating with SQL `GROUP BY`.

Both adapters must produce **identical aggregation semantics** regardless of the
query mechanism: the same top-10 cap (`StatisticsSummary.TOP_LIMIT`), the same
tie-break (count DESC, then key ascending — `clientIp` / `path`), 1-decimal
rounding of `avgThreatScore`, and samples ordered `timestamp` DESC. Anything
that changes one adapter's grouping, ordering, or rounding must change the other
in lockstep; the store-parity tests exist to enforce this.

> MongoDB was an evaluated storage candidate — benchmarked against Postgres and
> then removed. It now lives only on the `candidate/mongo-store` branch;
> PostgreSQL is the shipped external store.

## How it fits the pipeline

analytics is a strict **read-only consumer** of the shared `events` schema that
`event-store` owns and writes. Because it never touches Kafka, its only
dependencies on the rest of the system are the shared **enums**
(`AttackCategory`, `Action`, `Severity`) and that shared store schema.

The authoritative API and shared enums are documented in
**`services/contracts/ABOUT.md`**; any contract change is logged in
**`services/contracts/CHANGELOG.md`** — check there when the schema or enums
move, since this service reads them directly.

## Bonuses owned here

All three analytics bonuses are implemented on the `bonuses` branch.

- **B3 time-series** — `GET /v1/stats/timeseries` returns event counts bucketed
  by a `1m`/`5m`/`1h` `interval` (sparse, ascending). The `BuildTimeSeries` use
  case drives the new `timeSeries` port method; both read adapters bucket
  identically.
- **B1 alerting** — "N events of category X within Y minutes" rules.
  `POST /v1/alerts/define` (`DefineAlertRule`) registers a rule; `GET
  /v1/alerts/evaluate` (`EvaluateAlerts`) counts each rule's category over its
  window via the new `countByCategoryWithin` port method and flags `firing`.
  Rules live in an in-memory `AlertRuleStore` (`infrastructure/alert`), not
  persisted across restart.
- **B4 rate-limiting** — per-client-IP throttling that returns `429`
  (`infrastructure/ratelimit/*` + `RateLimitConfiguration`). It covers only
  `/v1/stats` and `/v1/events`; `/v1/alerts/**` is intentionally exempt.

## Key files

```
interfaces/rest/       StatsController, SamplesController, PingController,
                       response DTOs, ApiExceptionHandler (400 mapping)
application/           SummarizeStatistics / FetchEventSamples use cases
domain/model           EnrichedEventView (the read view)
domain/port            AnalyticsReadStore (the query port)
domain/query           StatisticsQuery/Summary, SampleQuery, EventSamplesPage,
                       Category/Attacker/Path statistics, TimeRange
infrastructure/persistence/inmemory   InMemoryAnalyticsReadStore (seeded)
infrastructure/persistence/postgres   PostgresAnalyticsReadStore (SQL GROUP BY)
infrastructure/config  ReadStoreConfiguration (the two @Beans) + Postgres
                       auto-config import filter
infrastructure/seed    DevDataSeed (demo data for inmemory mode)
```

Hexagonal throughout: the domain imports no Spring or persistence types; only
the adapters know how the data is stored.
