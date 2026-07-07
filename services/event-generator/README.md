# event-generator — Part 5 seedable CLI that synthesizes security events and attack waves

A command-line tool that generates realistic, deterministic security events
(with repeated-IP+path attack bursts) and emits them via one of three output
modes. In the pipeline it sits at the front: **event-generator** → gateway
`POST /v1/events/ingest` (`:8081`) → enrichment → event-store → analytics. It is
**not a long-running service**: `spring.main.web-application-type: none`, so a
`CommandLineRunner` generates the dataset, writes/feeds it, then the process
exits.

## Responsibilities

- **Deterministic synthesis** — a fixed `seed` reproduces the exact same dataset.
  Events draw from realistic pools (7 rule categories, 4 severities, 3 actions,
  real-looking paths, hostnames, geo, user-agents including `sqlmap`/`curl`,
  config ids `14227/20351/88120`). Event shape mirrors the gateway's ingest DTO /
  `RawEventMessage` in `../contracts`.
- **Attack waves** — beyond background traffic, it emits `wave-count` bursts of
  `wave-size` events that hammer a single attacker IP + target path within a
  ~120s window (one hour apart), which is what trips the enrichment
  repeat-offender rule downstream.
- **Three output modes** — `HTTP` (batched POST to the gateway), `JSON_FILE`
  (write a JSON array to disk), `STDOUT` (print the JSON array).

## Entry points

The only entry point is `GeneratorRunner` (a Spring `CommandLineRunner`). Drive
it with `spring-boot:run`, overriding any `wsa.generator.*` property through
`-Dspring-boot.run.arguments`:

```bash
# HTTP — feed a running gateway
mvn -pl services/event-generator spring-boot:run \
  -Dspring-boot.run.arguments="\
    --wsa.generator.output-mode=HTTP \
    --wsa.generator.target-url=http://localhost:8081 \
    --wsa.generator.total-events=10000 \
    --wsa.generator.wave-count=5 \
    --wsa.generator.batch-size=200"

# JSON_FILE — write a JSON array to disk (no gateway needed)
mvn -pl services/event-generator spring-boot:run \
  -Dspring-boot.run.arguments="\
    --wsa.generator.output-mode=JSON_FILE \
    --wsa.generator.output-file=events.json \
    --wsa.generator.total-events=500"

# STDOUT — print the JSON array (no gateway needed)
mvn -pl services/event-generator spring-boot:run \
  -Dspring-boot.run.arguments="--wsa.generator.output-mode=STDOUT --wsa.generator.total-events=20"
```

## Configuration

All properties are bound under `wsa.generator.*` (`GeneratorProperties`). Defaults
below are the shipped `application.yml` values.

| Property | Default | Meaning |
|----------|---------|---------|
| `seed` | `42` | RNG seed; same seed → identical dataset. |
| `total-events` | `10000` | Total events generated (background + wave). |
| `wave-count` | `20` | Number of attack-wave bursts. |
| `wave-size` | `50` | Events per wave (all from one IP hitting one path). |
| `base-timestamp` | `2026-05-20T14:00:00Z` | Start instant; background events step +1s, wave N starts at base + N hours. |
| `target-url` | `http://localhost:8081` (env `GATEWAY_URL`) | Gateway base URL for `HTTP` mode; `/v1/events/ingest` is appended. |
| `batch-size` | `200` | Events per POST batch in `HTTP` mode. |
| `output-mode` | `STDOUT` | One of `HTTP`, `JSON_FILE`, `STDOUT`. |
| `output-file` | `generated-events.json` | Destination file for `JSON_FILE` mode. |

Note: total wave events are capped at `total-events` (`wave-count * wave-size`,
whichever is smaller), and the remainder is background traffic. In `HTTP` mode a
rejected batch (non-2xx) is logged and counted as 0 accepted; other batches
continue.

## Run

Use the `spring-boot:run` invocations above. Only `HTTP` mode needs a reachable
gateway — bring the stack up with `docker compose up` (from the repo root) or run
a local gateway on `:8081` (`mvn -pl services/gateway spring-boot:run`).
`STDOUT` and `JSON_FILE` need nothing external.

## Build & test

```bash
mvn -pl services/event-generator -am test
```

`-am` also builds the `contracts` dependency. Tests cover property binding, the
deterministic generator, wave shaping, the JSON writer, and the batching REST
client.

## Internal layout

```
src/main/java/com/akamai/wsa/generator/
  GeneratorApplication.java     @SpringBootApplication + @ConfigurationPropertiesScan
  GeneratorProperties.java      wsa.generator.* record + OutputMode enum
  GeneratorRunner.java          CommandLineRunner: generate → dispatch by output-mode
  generate/
    SecurityEventGenerator.java realistic single-event synthesis (normal + wave)
    DatasetGenerator.java       background traffic + attack-wave assembly
  feed/
    IngestionClient.java        port: postBatch(...)
    RestClientIngestionClient.java  POSTs to {target-url}/v1/events/ingest
    IngestionFeeder.java        splits events into batch-size POSTs, sums accepted
  output/
    JsonEventWriter.java        serializes events to a JSON array
  model/
    GeneratedEvent.java, GeneratedRule.java, GeneratedGeoLocation.java
```

## Related

- The repo's `/generate` skill is a thin wrapper that drives this module against
  a running pipeline (seeding data / firing attack waves) — this README is the
  underlying tool it invokes.
- [`../contracts`](../contracts) — the event shape emitted here mirrors the
  gateway's ingest DTO / `RawEventMessage`, the single source of truth.
- Root [`../../README.md`](../../README.md) — full pipeline overview.
