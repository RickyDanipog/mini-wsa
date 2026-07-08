# Contracts CHANGELOG

> Append an entry for **every** change to a shared type in `ABOUT.md`. This log
> is the broadcast channel: each service's `ABOUT.md` instructs its
> agent to read this file before implementing, so a contract move is seen by all.

Format: `## YYYY-MM-DD — <summary>` · what changed · affected services · required follow-up.

---

## 2026-07-07 — Mongo `events` collection contract removed (Postgres won the benchmark)

Removed the shared Mongo `events` collection schema and the MongoDB adapters from `main`. PostgreSQL was chosen after the storage benchmark (`docs/storage-benchmark.md`): stats-aggregation p95 431 ms vs Mongo 1468 ms (3.4×), samples p95 36 ms vs 106 ms, persist 1302 vs 1104 ev/s. The Postgres `events` table remains the live shared store contract. Redis (offender window) is unaffected. The Mongo adapters + the full benchmark harness are preserved on the `candidate/mongo-store` branch.

**Affected services:** event-store (MongoEventStore removed), analytics (MongoAnalyticsReadStore removed). Shipped modes are now `inmemory` (default) and `postgres`.

## 2026-07-07 — Shared Postgres `events` table schema added

Added the relational `events` table (DB `wsa`) as a cross-service contract for the Postgres storage-candidate phase, mirroring the Mongo `events` collection: event-store is the sole writer (`INSERT ... ON CONFLICT (event_id) DO NOTHING`, first-write-wins), analytics reads it read-only. Flat columns (rule/geo flattened to `rule_*` / `geo_*`), VARCHAR enum-name encoding, `TIMESTAMPTZ` for `Instant` (bind/read as `OffsetDateTime` UTC), indexes `(config_id,timestamp)` / `(client_ip,timestamp)` / `(timestamp)`. See ABOUT.md "Shared Postgres `events` table".

**Affected services:** event-store (PostgresEventStore writer), analytics (PostgresAnalyticsReadStore reader). Both gated on `wsa.storage=postgres`; JDBC auto-config gated by a `PostgresStorageAutoConfigurationFilter` so non-postgres modes open no DataSource.

## 2026-07-07 — Shared Mongo `events` collection schema added

Added the `events` collection schema (DB `wsa`) as a cross-service contract for the Mongo storage phase: event-store is the sole writer (idempotent upsert by `_id`=eventId), analytics reads it read-only. Flat document + nested `rule`/`geoLocation`, enum-name encoding, indexes `{configId,timestamp}` / `{clientIp,timestamp}` / `{timestamp}`. See ABOUT.md "Shared Mongo collection".

**Affected services:** event-store (MongoEventStore writer), analytics (MongoAnalyticsReadStore reader). Both gated on `wsa.storage=mongo`.

## 2026-07-07 — Initial contracts (v1)

Established the shared message API (schema version 1):
- `MessageEnvelope<T>` — `int version` + 3-arg `of(correlationId, occurredAt, payload)`.
- `RawEventMessage` (flat DLR fields; `clientIp` is a String).
- `EnrichedEventMessage` — **nested**: original fields under `.rawEvent()`, plus `attackType` (String display name), `threatScore` (int), `receivedAt`.
- `RuleMessage`, `GeoLocationMessage`, and enums `AttackCategory` / `Action` / `Severity`.
- Topics `events.raw` (key clientIp) and `events.enriched` (key configId).

**Affected services:** gateway (produces raw), enrichment (consumes raw, produces enriched), event-store (consumes enriched). analytics reads Mongo, not Kafka — unaffected. event-generator sends the gateway's REST DTO, which must mirror `RawEventMessage`.

**Follow-up:** the per-service plans written before this module existed assumed a
different shape (String version, flat `EnrichedEventMessage`, a `from(...)`
factory, a `contracts.AttackType`). All three Kafka-touching plans were patched
to this v1 API on 2026-07-07.
