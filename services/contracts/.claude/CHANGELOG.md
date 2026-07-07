# Contracts CHANGELOG

> Append an entry for **every** change to a shared type in `context.md`. This log
> is the broadcast channel: each service's `.claude/context.md` instructs its
> agent to read this file before implementing, so a contract move is seen by all.

Format: `## YYYY-MM-DD — <summary>` · what changed · affected services · required follow-up.

---

## 2026-07-07 — Shared Mongo `events` collection schema added

Added the `events` collection schema (DB `wsa`) as a cross-service contract for the Mongo storage phase: event-store is the sole writer (idempotent upsert by `_id`=eventId), analytics reads it read-only. Flat document + nested `rule`/`geoLocation`, enum-name encoding, indexes `{configId,timestamp}` / `{clientIp,timestamp}` / `{timestamp}`. See context.md "Shared Mongo collection".

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
