# Gateway — Service Context

**Role:** the write-path entry point of Mini WSA. Accepts security events over REST, validates them, transforms them to the shared contract, and publishes them to Kafka. **Stores nothing.**

- **Port:** 8081 · **Base package:** `com.akamai.wsa.gateway`
- **Inbound:** `POST /v1/events/ingest` (single object or array/batch)
- **Outbound:** publishes `MessageEnvelope<RawEventMessage>` to topic `events.raw`, keyed by `clientIp`
- **Owns:** request validation, DTO→contract transform, correlationId minting. Holds no state/DB (the "in-memory-first" storage decision doesn't touch this service).

## Behavior contract
- **All-or-nothing** batch validation → `201 {acceptedCount}` on success, `400 {error:{code,message,details[]}}` if any event is invalid. No `{success,data}` envelope.
- Validates: required fields, enum binding (`AttackCategory`/`Action`/`Severity`), ISO-8601 timestamp.
- Mints `correlationId` (`x-correlation-id` header or generated); sets envelope `occurredAt` from an injected `Clock`.
- **Does NOT stamp `receivedAt`** — enrichment does (SDD §5.1). `occurredAt` records gateway accept-time.

## Where to look
- Plan: `services/gateway/.claude/plans/plan.md` · Reconciliation: `./plans/reconciliation.md`
- SDD v2: `docs/03-sdd.md` §3 (responsibilities), §4 (contracts/topics), §5.1 (write path), §8 (API), §9 (scaling)
- Conventions: root `AGENTS.md` (Java 21, Spring Boot 3.3.5, hexagonal, records, immutability, full parameter names, single-line logging with correlationId).

## SHARED CONTRACTS — read before implementing
This service produces `events.raw`. **Read `services/contracts/.claude/context.md`** — the authoritative API. Key facts: `MessageEnvelope` uses `int version` + 3-arg `MessageEnvelope.of(...)`; `RawEventMessage.clientIp` is a `String`. **Any contract change is logged in `services/contracts/.claude/CHANGELOG.md`** — check it before implementing; a change there requires re-checking this plan and the `events.raw` (de)serialization pairing with the enrichment consumer.
