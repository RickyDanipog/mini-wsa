# event-generator — Plan Reconciliation Report

> Read-only reconciliation of `docs/superpowers/plans/2026-07-07-part5-generator.md`
> against the assignment (Part 5 — Data Generation), SDD v2 (`docs/03-sdd.md`),
> and the v2 restructure. Branch: `feature/v2-restructure`. No source modified.

## Summary
The generator plan is **substantially aligned** and largely v2-safe (it already
targets port 8081, which the gateway now owns). The gaps are all at the *edges* —
a stale root-pom snippet, "mini-wsa"→"gateway" wording, and the feeder's handling
of the gateway's real response contract (`201 {acceptedCount}` / `400` on a
rejected batch). No storage concerns apply (the generator is stateless; the
"cached-memory-first" decision does not touch it).

## What aligns
- **Realistic randomized events** — `SecurityEventGenerator` emits valid enums
  (category/severity/action), plausible IPs/paths/hosts/sizes. ✅ (assignment)
- **Attack waves** — `DatasetGenerator` bursts `waveSize` events from one IP on
  one path within a span; a test asserts a `>5`-in-10-min group exists. ✅
  (assignment + exercises the enrichment repeat-offender rule)
- **Configurable count / waves / seed / batch** — `GeneratorProperties`. ✅
- **Deterministic seeding** — all randomness from an injected `Random`;
  timestamps from a fixed base `Instant`; no `Instant.now()`/`Math.random()`.
  ✅ matches SDD's "seedable/deterministic" requirement.
- **Output modes** — JSON file / STDOUT / **HTTP POST to the ingest API**,
  batched. ✅ (assignment "feedable into the ingestion API")
- **CLI shape** — `web-application-type: none`, `CommandLineRunner`. ✅ matches
  SDD "event-generator = CLI".
- **Port 8081** — the plan's default `http://localhost:8081/v1/events/ingest`
  now points at the **gateway** (same port). ✅ by good luck, not by design.
- **Batch (array) POST** — the gateway accepts single **or** array. ✅

## Missing coverage
- **Gateway response accounting** — the plan's `RestClientIngestionClient`
  treats any 2xx as "all accepted" and returns `batch.size()`. The gateway
  returns `201 {"acceptedCount": N}`; the feeder should parse it for accurate
  reporting.
- **Rejected-batch handling** — the gateway is **all-or-nothing per batch**: one
  invalid event → `400` for the whole batch. `RestClient.retrieve()` throws on
  4xx, so a single bad batch would **abort the whole run**. Not covered — the
  feeder needs to catch/log a 400 and continue.
- **Direct-Kafka producer mode** — SDD §2 notes the generator "later can produce
  directly to Kafka". Only the HTTP feeder exists. Deferred, not required for
  Part 5 — flagged for awareness.
- **correlationId (optional)** — the generator sends none; the gateway mints one
  when absent, so this is acceptable, but an optional `x-correlation-id` per
  batch would aid end-to-end tracing in dry runs.

## Conflicts
- **Stale root-pom snippet** — Task 1 Step 3 shows adding the module to a
  *two-module* list (`mini-wsa` + `event-generator`). The v2 reactor already has
  **six** modules (`contracts`, `gateway`, `enrichment`, `event-store`,
  `analytics`, `mini-wsa`). The step must append `services/event-generator` to
  the current list, not replace it.
- **Dry-run target naming** — Task 5 Step 8 says "start **mini-wsa** on 8081".
  In v2 that is the **gateway**. Behaviour also differs: the gateway *publishes
  to Kafka* rather than storing, so a POST returning 201 means "accepted for
  processing", not "persisted". A dry run that verifies persistence needs the
  gateway **+ enrichment + event-store + Kafka** up (via docker-compose); a
  generator↔gateway-only run validates ingestion acceptance.

## Stale assumptions
- **"mini-wsa on 8081"** throughout Global Constraints (line 19) and Task 5 —
  should read "gateway". The contract that matters (`POST /v1/events/ingest`,
  single/array, `201`) is preserved by the gateway plan, so only wording is stale.
- **Own `Generated*` records vs `contracts`** — the plan defines its own
  `GeneratedEvent`/`GeneratedRule` (Strings for enums) rather than reusing the
  new `contracts` module. This risks drift from the gateway's `IngestEventRequest`
  shape. Both derive from the assignment DLR schema so they *should* match, but a
  shared representation would remove the drift risk.

## Recommended next steps (ordered)
1. **Fix the module registration step** to append `services/event-generator` to
   the v2 six-module reactor list.
2. **Reword** all "mini-wsa on 8081" references to "gateway on 8081"; add a note
   that full-pipeline dry runs also require enrichment + event-store + Kafka.
3. **Harden the feeder**: parse `201 {acceptedCount}` for accurate totals and
   **catch `400`** (rejected batch) — log the rejection and continue rather than
   aborting the run.
4. **Guarantee schema validity** of generated events so all-or-nothing batches
   pass (already true); optionally add a config flag to inject a small fraction
   of *invalid* events to exercise the gateway's 400 path as negative test data.
5. **Consider reusing `contracts`** (or a shared request DTO) so generated JSON
   stays in lockstep with the gateway's `IngestEventRequest`.
6. **(Future)** add a direct-Kafka output mode per SDD §2 — not required for the
   Part 5 deliverable.

_No storage / cached-memory reconciliation needed: the generator persists
nothing._
