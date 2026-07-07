# Gateway — Plan Reconciliation Report

_Service: `gateway` (:8081) · Reconciled against SDD v2 (§3/§4/§5.1/§8/§9), assignment Part 1 (Ingestion), AGENTS.md, and the current `feature/v2-restructure` code._

Sources: `2026-07-07-service-gateway.md` (authoritative), `2026-07-07-part1-ingestion.md` (superseded reference), `2026-07-07-v2-restructure.md` (Task 4 wiring).

## What aligns
- **Endpoint & shapes:** `POST /v1/events/ingest`, single object *or* array, all-or-nothing → `201 {acceptedCount}` / `400 {error:{code,message,details[]}}`, no `{success,data}` envelope — matches SDD §8 and assignment Part 1.
- **Validation:** required fields (`@NotBlank`/`@NotNull`), enum binding (`Action`/`Severity`/`AttackCategory`), ISO-8601 via `Instant` — matches assignment.
- **Write-path role:** validates → maps DTO → `RawEventMessage` → publishes enveloped, `clientIp`-keyed to `events.raw`; **stores nothing** — matches SDD §3/§4/§5.1.
- **correlationId** minted from `x-correlation-id` or generated (SDD §9). Port `8081`.
- **Hexagonal:** pure `EventRequestMapper`, outbound `RawEventPublisher` port + Kafka adapter, `domain` free of Spring/Kafka — matches AGENTS §3/§4.
- Storage-agnostic: gateway holds no store, so the "in-memory/cached for now" decision needs nothing here. ✅

## Missing coverage
- **`receivedAt` "on ingestion":** the assignment says a server-side `receivedAt` is assigned *on ingestion*. In v2 the gateway does **not** stamp it — SDD §5.1/§8 defers it to enrichment. The enriched event still gets a `receivedAt`, but under Kafka lag it reflects *processing* time, not *accept* time. Not a code gap; a semantic decision to confirm (see next steps). The envelope already carries `occurredAt` (gateway accept time), which could carry the intended value.
- No other Part-1 requirement is unaddressed.

## Conflicts
- **Envelope `version` type mismatch (won't compile).** Task 3's controller builds `new MessageEnvelope<>(correlationId, occurredAt, SCHEMA_VERSION, rawEventMessage)` with `SCHEMA_VERSION = "1"` (a `String`), but `contracts.MessageEnvelope.version` is an **`int`** (with `CURRENT_VERSION = 1` and a `MessageEnvelope.of(correlationId, occurredAt, payload)` factory). Use the factory (preferred) or pass the int.
- **Producer/consumer serialization pairing (cross-service).** Task 2 sets a `JsonSerializer` for `MessageEnvelope<RawEventMessage>`; enrichment's consumer must be configured with a matching type (trusted packages / `TypeReference`) or the round-trip fails. Coordinate with the enrichment plan.

## Stale assumptions
- **Kafka starter not present yet.** The plan's Global Constraints assume the scaffold already has `spring-kafka`, but restructure Task 2 **deferred** the Kafka/Mongo/Redis starters — the current `services/gateway/pom.xml` has only web/validation/actuator/contracts/test. The plan (Task 2 note) mentions adding `spring-kafka-test` + `awaitility` but **not** the main `spring-kafka` starter. Must be added.
- **`Clock` bean.** The plan assumes a `Clock` may exist from the restructure phase; it does not — gateway must define `@Bean Clock clock() { return Clock.systemUTC(); }`.
- **`part1-ingestion.md`** describes the monolith (store-then-return); the service plan correctly supersedes it with publish-instead-of-store. Treat part1 as logic reference only.

## Recommended next steps (ordered)
1. Add `spring-kafka` (main) + `spring-kafka-test` and `awaitility` (test) to `services/gateway/pom.xml`.
2. Fix envelope construction to match `contracts.MessageEnvelope` — use `MessageEnvelope.of(correlationId, occurredAt, rawEventMessage)` (drops the stray `String` version).
3. Add a `Clock` bean in a gateway `infrastructure/config`.
4. Decide `receivedAt`: accept SDD's enrichment-stamped value, **or** stamp at the gateway (== `occurredAt`) and carry it through so it reflects true ingestion time — reconcile with the assignment wording.
5. Execute Tasks 1→3 TDD (mapper → publisher → controller), then dry-run against a compose Kafka, tag `v0.2-gateway`.
6. Agree the `events.raw` (de)serialization contract with the enrichment consumer before wiring the pipeline (restructure Task 4).

_Read-only report; no source modified._
