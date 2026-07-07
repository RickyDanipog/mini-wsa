# Enrichment Service — Reconciliation Report

> Read-only reconciliation of `docs/superpowers/plans/2026-07-07-service-enrichment.md`
> (authoritative) against SDD v2 §6/§5.1/§9/§14, the assignment (Part 2), `AGENTS.md`,
> and the current `services/enrichment/` scaffold on `feature/v2-restructure`.
> Date: 2026-07-07. Storage stance: **in-memory offender window first** (Redis deferred) — per the "cached memory for dry runs" directive; Redis absence is *not* flagged as missing.

## What aligns

- **Classification** (`AttackType.fromCategory` → display name) matches the assignment mapping table and SDD §6.1.
- **Scoring weights** exactly match the assignment: severity 40/30/20/10, action 20/10/0, sensitive path (`/admin`|`/login`) +15, repeat-offender +15, **cap at 100**. Worked examples (75, 90) correct.
- **Pure rule-object scorer (Logic 2)** with the offender flag injected via `ThreatScoringInputs` — matches SDD §6.1 and the AGENTS "scorer is pure" rule. Domain imports no Spring/Kafka/Redis; beans wired in `EnrichmentConfiguration`.
- **Repeat-offender semantics** — record-then-read, `receivedAt` basis, `count > 5`, **6th event first flagged** — match SDD §6.2 (post-reconcile) and the locked decision. No conflict with the SDD here.
- **`OffenderWindow` port** = `recordEvent` + `countRecentEventsFromClient` — matches SDD §6.2.
- **Topology**: consume `events.raw` → publish `events.enriched` keyed by `configId`; `correlationId` preserved — matches SDD §3/§4/§5.1/§9.
- In-memory window first (Task 5) with Redis adapter deferred (Task 6) — matches the dry-run-first directive.

## Missing coverage

1. **Idempotency / dedup on `eventId` (SDD §14.2).** Consumption is at-least-once; on redelivery the plan would `recordEvent` again (**inflating the offender count** → wrong score) and re-publish a duplicate enriched event. The plan has no dedup. This is the most important gap. Recommend: dedupe by `eventId` in the listener (skip already-seen) or make `recordEvent` idempotent per `(clientIp, eventId)`.
2. **Dead-letter handling (SDD §9).** No error path for poison/undeserializable messages on `events.raw`. Add a DLT or error handler (hardening).
3. **Multi-instance correctness note.** The in-memory window is **per-instance**; correct only single-instance. Fine for dry runs, but the plan should state that horizontal scaling requires the Redis window (Task 6) *and* `events.raw` partitioned by `clientIp` (SDD §4) so one IP's events land on one consumer.

## Conflicts (plan vs actual committed `contracts`)

These are concrete API mismatches — the plan was written before `contracts` was committed and assumes a slightly different surface:

1. **`AttackType` is not in `contracts`.** The plan imports `com.akamai.wsa.contracts.AttackType` (Task 1), but the committed `contracts` module only has `AttackCategory`, `Action`, `Severity`. `AttackType` currently lives only in the (to-be-retired) `mini-wsa` domain. **Resolve:** define `AttackType` in the enrichment domain (`domain/model`) — the wire form is already a `String` display name in `EnrichedEventMessage`, so it needn't be shared — or add it to `contracts`. Recommend enrichment-local.
2. **`MessageEnvelope.of` arity.** Committed signature is `of(String correlationId, Instant occurredAt, T payload)` (3-arg). The plan's publisher calls `MessageEnvelope.of(correlationId, enrichedEventMessage)` (2-arg). **Resolve:** pass an `occurredAt` (e.g. the enriched `receivedAt` or `clock.instant()`).
3. **`EnrichedEventMessage.from(...)` does not exist.** Committed `EnrichedEventMessage` is a record composing a nested `RawEventMessage`: `(RawEventMessage rawEvent, String attackType, int threatScore, Instant receivedAt)`. The plan assumes a flat `from(rawEventMessage, attackType, threatScore, receivedAt)` factory. **Resolve:** use the constructor `new EnrichedEventMessage(rawEventMessage, attackType, threatScore, receivedAt)`, or add that factory to `contracts`.
4. **`EnrichedEventMessage.configId()` accessor doesn't exist.** The publisher keys the partition on `enrichedEventMessage.configId()`, but `configId` is nested: use `enrichedEventMessage.rawEvent().configId()`.

None of these are logic conflicts (scoring/offender semantics agree with the SDD) — they are wiring/shape mismatches against the real `contracts`.

## Stale assumptions

- Plan predates the committed `contracts` (items in Conflicts above).
- Plan's `ThreatScore` VO duplicates `mini-wsa`'s — expected during migration; `mini-wsa` is retired in restructure Task 5, so the enrichment-local copy is correct going forward.
- Plan is already correctly Kafka-consumer-shaped (not the monolith REST shape) and uses record-then-read (not the superseded part2 prior-only). No monolith/prior-only staleness remains.

## Recommended next steps (ordered)

1. **Before coding, patch the plan's `contracts` touchpoints** (Conflicts 1–4): define `AttackType` in enrichment domain; use the 3-arg `MessageEnvelope.of`; construct `EnrichedEventMessage` via its record constructor; key the publisher on `rawEvent().configId()`.
2. Execute Tasks 1–5 as written (classifier, VOs, rules, calculator, in-memory `OffenderWindow`) — logic is sound and matches the SDD.
3. **Add eventId dedup** in the `RawEventListener` (or make `recordEvent` idempotent) to satisfy SDD §14.2 before the pipeline test.
4. Wire Task 7 (config → service → listener → publisher) with the patched `contracts` calls; keep the `@EmbeddedKafka` test using the in-memory `OffenderWindow` (no Redis needed for dry runs).
5. Defer Task 6 (Redis adapter) and DLT/error-handling to the hardening pass, after the in-memory pipeline is green end-to-end.
6. Note the per-instance window limitation + `events.raw` clientIp-partitioning assumption in the service README.
