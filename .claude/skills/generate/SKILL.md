---
name: generate
description: Seed the running Mini WSA pipeline with realistic security events and attack waves by running the event-generator module against the gateway. Use when the user asks to generate or seed test data, fire an attack wave, or produce sample events for the pipeline.
---

# /generate — seed the Mini WSA pipeline

Thin wrapper around the `services/event-generator` module (the graded Part 5 deliverable). NEVER reimplement event generation here — always drive the module so there is one source of truth for the event shape and attack-wave logic.

## Arguments (all optional; parse from the user's phrasing)
- `count` — total events (default 200 for a quick seed; use 10000 for a full load)
- `waves` — attack-wave bursts of repeated IP+path (default 5)
- `mode` — the generator's `wsa.generator.output-mode` enum: `HTTP` (POST batches to the gateway — use this to seed a running pipeline, default here) | `JSON_FILE` | `STDOUT`
- `target` — gateway URL (default `http://localhost:8081`)

## Steps
1. If `mode` is `FEED`, confirm the pipeline is up:
   `curl -fs http://localhost:8081/actuator/health`
   If it fails, tell the user the pipeline isn't running and suggest `docker compose up --build -d` (needs at least kafka + gateway + enrichment + event-store), then stop.
2. Run the generator with overrides (host JVM, targets the mapped gateway port):
   ```
   export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
   mvn -q -pl services/event-generator spring-boot:run \
     -Dspring-boot.run.arguments="--wsa.generator.total-events=<count> --wsa.generator.wave-count=<waves> --wsa.generator.output-mode=<mode> --wsa.generator.target-url=<target>"
   ```
   The generator is a CLI (`web-application-type: none`); its CommandLineRunner generates, feeds/writes, then exits.
3. Report the accepted counts it logged. For `FEED`, optionally show a few resulting scores:
   `docker exec wsa-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic events.enriched --from-beginning --timeout-ms 8000 --max-messages 5`
   and summarize the `threatScore`s (expect repeat-offender bursts to reach 90).

## Notes
- `FEED` produces real load against the running gateway — say so before firing a large `count`.
- Deterministic: the generator is seeded (`wsa.generator.seed`), so the same config reproduces the same events.
- This skill only *runs* the module; it never edits it.
