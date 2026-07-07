# event-store — Plan ↔ Requirements Reconciliation

> Read-only reconciliation. Compares `docs/superpowers/plans/2026-07-07-service-event-store.md`
> (authoritative) and the superseded `part3-stats`/`part4-samples` storage logic
> against SDD v2 (§3, §5.1, §7, §14), `AGENTS.md`, the assignment's storage
> requirement, and the current code on `feature/v2-restructure`.
> Date: 2026-07-07.

## What aligns

- **Role & flow.** Plan matches SDD v2: event-store **consumes `events.enriched`**
  and is the **system of record** (write side); analytics reads a Mongo replica.
- **Assignment storage requirement.** `StoredEvent` carries every original DLR
  field (incl. `method`, `statusCode`, `userAgent`, `requestSize`, `responseSize`,
  nested rule/geo) **plus** `attackType`, `threatScore`, `receivedAt` — full-fidelity
  persistence, exactly what the assignment asks to store.
- **In-memory first (matches the standing directive).** Task 1 builds an
  in-memory `EventStore` adapter; Mongo (Task 4) and Postgres (Task 5) come later.
  This is aligned with "start with cached memory for dry runs/sanity; handle DB
  candidates once the logic is perfected."
- **Idempotency.** Plan dedupes on `eventId` (in-memory `putIfAbsent`, Mongo
  `_id = eventId`) — correct for Kafka at-least-once (SDD §14).
- **Indexing.** Mongo compound indexes `(configId, timestamp)` and
  `(clientIp, timestamp)` match SDD §4.3.
- **Hexagonal purity.** Domain imports only `java.*` + `contracts` enums; Spring/
  Kafka/Mongo confined to `infrastructure`/`interfaces` — matches AGENTS §3.
- **receivedAt ownership.** Plan takes `receivedAt` from the enriched message
  (stamped upstream in enrichment) and just persists it — matches SDD §5.1/§8.

## Missing coverage

- **Indexes for the analytics read path.** event-store owns the `events`
  collection that analytics queries (stats/samples) via the replica, but the plan
  only indexes for `findByConfigId` + the repeat-offender shape. Analytics filters
  by `category`/`action` and sorts by `timestamp desc` — the collection should
  also carry indexes serving those query patterns. *Recommend coordinating the
  index set with the analytics plan (shared-schema consequence).*
- **Poison-message / DLT handling.** SDD §9 mentions a dead-letter topic for bad
  messages; the plan's listener has no error/DLT path. Minor, but note it.

## Conflicts (plan vs actual)

1. **⚠️ Mapper assumes a FLAT `EnrichedEventMessage`; the real contract is NESTED.**
   The plan's `EnrichedEventMapper` calls `enrichedEventMessage.eventId()`,
   `.timestamp()`, `.rule()`, `.geoLocation()`, etc. But `contracts.EnrichedEventMessage`
   is `(RawEventMessage rawEvent, String attackType, int threatScore, Instant receivedAt)`
   — the raw fields live under **`.rawEvent().…`**. The mapper as written **will not
   compile**. Fix: read raw fields via `enrichedEventMessage.rawEvent()`, and the
   three enrichment fields directly. (The plan's hedge note is backwards — contracts
   *is* nested.)
2. **Generic Kafka deserialization risk.** The listener signature
   `onEnrichedEvent(MessageEnvelope<EnrichedEventMessage>)` relies on deserializing
   a generic envelope; Spring-Kafka `JsonDeserializer` erases generics and may
   deliver `MessageEnvelope<LinkedHashMap>`. Needs an explicit target type
   (`JsonDeserializer` with `useTypeHeaders`/`ParameterizedTypeReference`, a
   concrete non-generic wrapper, or a custom deserializer). Flag before Task 3.

## Stale assumptions

- **Scaffold pom.** Plan's Global Constraints say the scaffold pom already has
  `kafka` + `data-mongodb` starters. The actual scaffold (this branch) has only
  web/actuator/contracts/test — the messaging/Mongo starters were **deferred to
  Task 4** to keep scaffold tests green without brokers. Tasks 3–4 must add them.
- **"Mongo as Task 4 (soon)" vs the standing directive.** The plan sequences Mongo
  right after the listener. Per "cached memory for now, DB candidates later," Task 4
  (Mongo) should be **deferred to the DB-candidate phase**, not built immediately —
  the dry-run/sanity pipeline should run on the in-memory adapter.
- **`StoredEventFixtures` test helper** referenced by the plan does not exist yet
  (expected — created during Task 1).

## Recommended next steps (ordered; in-memory first)

1. **Fix the mapper** to the nested contract (`.rawEvent().…`) before writing it.
2. **Task 1** — `StoredEvent` + `EventStore` port + `InMemoryEventStore` (idempotent
   by `eventId`) + fixtures + unit tests. *This is the sanity/dry-run store.*
3. **Task 2** — `EnrichedEventMapper` (nested-aware) + test.
4. **Task 3** — Kafka listener on `events.enriched` persisting via the in-memory
   store; resolve the generic-deserialization approach; add consumer config.
   Verify the end-to-end dry-run pipeline (gateway→enrichment→event-store) on
   in-memory storage.
5. **Defer Task 4 (Mongo)** and **Task 5 (Postgres)** to the DB-candidate phase —
   when we do, coordinate the `events` document/index shape with analytics (shared
   replica schema) and add a DLT for poison messages.
