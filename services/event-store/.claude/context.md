# event-store — Service Context

**Role:** the enriched-event **system of record**. Consumes `events.enriched` from Kafka, maps the contract to its owned `StoredEvent` model, and persists idempotently (dedupe on `eventId`, because Kafka delivery is at-least-once). Write side only — it exposes no query API to analysts; **analytics reads a Mongo replica** of this service's data.

**Port:** 8083 (`/v1/ping` + `/actuator/health`; no business REST endpoints — it's a Kafka consumer).

**Inbound:** `@KafkaListener` on topic `events.enriched` (value `MessageEnvelope<EnrichedEventMessage>`, keyed by `configId`).

**Storage (in-memory-first):** dry runs and sanity checks run on the in-memory `EventStore` adapter. **MongoDB is the real target but is DEFERRED to the DB-candidate phase**; a **PostgreSQL** adapter comes later purely for a load-test comparison. Do not build the DB adapters during the dry-run pipeline. All behind one `EventStore` port — swapping the store never touches domain/listener.

**Design references:** SDD v2 §7 (storage & ownership), §5.1 (write path), §14 (idempotency/ordering/DLT). Conventions in root `AGENTS.md`.

**Hexagonal purity:** `domain/model` + `domain/port` import only `java.*` and `contracts` enums. Spring/Kafka/Mongo live in `infrastructure`/`interfaces` only. Full descriptive parameter names; single-line logging with `correlationId`.

## SHARED CONTRACTS
Read **`services/contracts/.claude/context.md`** before implementing anything that touches Kafka messages. Key facts this service depends on:
- `EnrichedEventMessage` is **nested** — original fields under `.rawEvent()`; `attackType` is a `String`; `threatScore` an `int`; no `contracts.AttackType`, no `from(...)` factory.
- `MessageEnvelope<T>` deserialization needs the **parametric** target type (`MessageEnvelope<EnrichedEventMessage>`), not a raw class.

**Contract changes are logged in `services/contracts/.claude/CHANGELOG.md`.** Any entry there affecting `EnrichedEventMessage`/`MessageEnvelope` requires re-checking this plan (the mapper and the consumer deserializer) before executing.

## Data ownership note
The `events` document/index shape is a **shared read contract** with analytics (replica). Keep it stable and documented; coordinate index changes with the analytics plan.
