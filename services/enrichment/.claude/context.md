# Enrichment Service — Context

**Role:** Classify + threat-score security events. Consume `events.raw`, enrich, publish `events.enriched`. The graded business logic (scoring) lives here.

**Runtime:** Spring Boot, port **8082**. Base package `com.akamai.wsa.enrichment`.
- Inbound: `@KafkaListener` on topic `events.raw` (`MessageEnvelope<RawEventMessage>`).
- Outbound: publish `MessageEnvelope<EnrichedEventMessage>` to `events.enriched`, keyed by `configId`.
- State: `OffenderWindow` (repeat-offender sliding window) — **in-memory first**, Redis deferred. Dedup via `ProcessedEventLog` (in-memory first).

**Design rules:**
- The scorer is a **pure function** — the repeat-offender flag is computed by the caller and injected via `ThreatScoringInputs`.
- `domain/` imports **no** Spring/Kafka/Redis — pure classes wired to beans in `infrastructure/config/EnrichmentConfiguration`.
- Composable **rule-object** scorer (Logic 2): one class per contributor.
- Locked semantics: record-then-read; current event counts; flag `count > 5` (the **6th** event in the 10-min window is first flagged); window basis = `receivedAt`.
- Full descriptive parameter names; records; single-line logging with correlationId.

**Spec references:** SDD `docs/03-sdd.md` §6 (enrichment logic), §5.1 (write path), §14 (idempotency/ordering). Assignment Part 2. Plan: `.claude/plans/plan.md`. Reconciliation: `.claude/plans/reconciliation.md`.

## SHARED CONTRACTS
This service produces/consumes Kafka messages defined in the shared `contracts` module. **Before implementing, read `services/contracts/.claude/context.md`** for the authoritative API (note: `MessageEnvelope.of` is 3-arg; `EnrichedEventMessage` is nested — build via constructor; `attackType` is a String display name; there is no `contracts.AttackType`). **Any contract change is logged in `services/contracts/.claude/CHANGELOG.md`** — read it before coding, and re-check this plan whenever a new entry appears there.
