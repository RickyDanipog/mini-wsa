# event-generator — Service Context

**Role:** the project's **test tool** (not a runtime service). Generates realistic, seed-deterministic security events — including **attack waves** (bursts from one IP hitting one path, >5-in-10-min to exercise the enrichment repeat-offender rule) — and either writes them as JSON or POSTs them in batches to the gateway.

**Shape:** CLI Spring Boot app (`spring.main.web-application-type: none`, a `CommandLineRunner`). **No server port.** Base package `com.akamai.wsa.generator`.

**How it fits the pipeline (SDD v2 §2, §5.1):**
- Feeds the **gateway** at `POST http://localhost:8081/v1/events/ingest` (single object or array/batch).
- The gateway is the write-path entry point; a `201 {acceptedCount}` means "accepted for processing" (published to Kafka), not "persisted". Verifying persistence/analytics needs the full pipeline (gateway + enrichment + event-store + Kafka) up via docker-compose.
- Future: a direct-Kafka producer output mode (deferred; not required for the Part 5 deliverable).

**Key requirements (assignment Part 5):** realistic events, attack waves, configurable count (e.g. 10,000), deterministic seeding, output feedable into the ingestion API. All randomness from an injected `java.util.Random`; timestamps from a fixed base `Instant` — **never** `Instant.now()`/`Math.random()`/unseeded `Random`. Same seed → identical dataset.

**Conventions:** Java 21, Spring Boot 3.3.5, records + immutability, intention-revealing names, FULL descriptive parameter names (no `v`/`e`/`idx`). Conventional commits; milestone tag `v0.6-generator`.

## SHARED CONTRACTS
The generated event JSON **must stay in lockstep with the gateway's `IngestEventRequest`, which mirrors `contracts.RawEventMessage`** (flat DLR fields + nested `rule`/`geoLocation`, enum names as Strings). Before implementing, read **`services/contracts/.claude/context.md`**. Any contract change is logged in **`services/contracts/.claude/CHANGELOG.md`** — when it changes, re-check this plan's `GeneratedEvent` shape (or switch to mapping `GeneratedEvent → contracts.RawEventMessage` to remove drift risk).

## Plan & reconciliation
- Implementation plan: `./plans/plan.md`
- Reconciliation report (findings this plan already addresses): `./plans/reconciliation.md`
