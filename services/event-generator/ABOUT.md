# event-generator

The Part 5 CLI tool of Mini WSA. It synthesizes realistic, deterministic
security events — background traffic plus attack-wave bursts — and either feeds
them to the gateway over HTTP or writes them as JSON. It is **not a long-running
service**: `spring.main.web-application-type: none`, so a `CommandLineRunner`
(`GeneratorRunner`) generates the dataset, dispatches it by output mode, and the
process exits. Base package `com.akamai.wsa.generator`.

## What it does

- **Deterministic synthesis.** A fixed `seed` (default `42`) reproduces the exact
  same dataset. All randomness comes from a single seeded `java.util.Random`, and
  timestamps step off a fixed `base-timestamp` — never `Instant.now()`,
  `Math.random()`, or an unseeded `Random`. Same seed → identical events.
- **Realistic events.** Each event draws from curated pools: 7 rule categories
  (`INJECTION`, `XSS`, `PROTOCOL_VIOLATION`, `DATA_LEAKAGE`, `BOT`, `DOS`,
  `RATE_LIMIT`), 4 severities, 3 actions, real-looking paths/hostnames/methods,
  geo (CN, RU, US, BR, IN, DE), config ids `14227/20351/88120`, and user-agents
  including `sqlmap` and `curl`. `DENY` maps to HTTP 403.
- **Attack waves.** On top of background traffic, it emits `wave-count` bursts of
  up to `wave-size` events that hammer a **single attacker IP + target path**
  within a ~120s window (each wave one hour apart). These bursts are what trip
  the enrichment repeat-offender rule downstream. Total wave events are capped at
  `total-events`; the remainder is background traffic.

## Output modes

`GeneratorRunner` dispatches on `wsa.generator.output-mode`:

- **`HTTP`** — batches events (`batch-size` per POST) to the gateway; a rejected
  (non-2xx) batch is logged, counted as 0 accepted, and the rest continue.
- **`JSON_FILE`** — writes a JSON array to `output-file`.
- **`STDOUT`** — prints the JSON array (the shipped default mode).

## How it feeds the pipeline

In `HTTP` mode it POSTs to the gateway at `{target-url}/v1/events/ingest`
(default `http://localhost:8081`, single object or batch array). The gateway is
the write-path entry point: a `201 {acceptedCount}` means "accepted for
processing" (published to Kafka), not "persisted". Verifying persistence or
analytics needs the full pipeline up (gateway → enrichment → event-store,
backed by Kafka and PostgreSQL) via docker-compose. `STDOUT` and `JSON_FILE`
need nothing external.

## Shared contract

The generated event JSON **must stay in lockstep with the gateway's
`IngestEventRequest`, which mirrors `contracts.RawEventMessage`** — flat DLR
fields plus nested `rule`/`geoLocation`, enum names as Strings. The single
source of truth is [`services/contracts/ABOUT.md`](../contracts/ABOUT.md); any
change is logged in [`services/contracts/CHANGELOG.md`](../contracts/CHANGELOG.md).
When it changes, re-check the `GeneratedEvent` shape here.

## Key files

- `GeneratorProperties` — `wsa.generator.*` binding + `OutputMode` enum.
- `GeneratorRunner` — the `CommandLineRunner`: generate → dispatch by mode.
- `generate/SecurityEventGenerator` — single-event synthesis (normal + wave).
- `generate/DatasetGenerator` — assembles background traffic + attack waves.
- `feed/` — `IngestionClient` port, `RestClientIngestionClient` (POSTs batches),
  `IngestionFeeder` (splits into batches, sums accepted).
- `output/JsonEventWriter` — serializes events to a JSON array.
- `model/` — `GeneratedEvent`, `GeneratedRule`, `GeneratedGeoLocation`.

See the [README](./README.md) for run commands and the full property table. The
repo's `/generate` skill is a thin wrapper that drives this module against a
running pipeline.
