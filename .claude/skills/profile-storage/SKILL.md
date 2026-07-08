---
name: profile-storage
description: Benchmark the Mini WSA storage candidates (in-memory, MongoDB, PostgreSQL, ...) behind the EventStore port under a fixed, seeded load, AND find horizontal-scaling sweet spots (service replicas x Kafka partitions). Produces a comparison report to justify the DB choice and the scaling knee. Use when the user wants to profile, load-test, or compare database candidates, decide which store to adopt, or find how many instances/partitions to run.
---

# /profile-storage — benchmark the DB candidates

Purpose: run a fair, repeatable load test against each implemented storage adapter and produce a comparison so the storage choice is justified with data (the assignment requires justifying it in the README). The DB choice mainly affects **event-store** (write throughput / end-to-end lag) and **analytics** (aggregation + sample-query latency) — those are what we measure.

## Preconditions
Discover which candidates actually exist (Spring profiles / adapters): in-memory is always present; `mongo`, `postgres`, etc. only if their adapters have been built. If only in-memory exists, tell the user there is nothing to compare yet — the DB-candidate adapters must be implemented first — and stop.

## Method (per candidate — keep it FAIR)
1. Bring up the pipeline configured for that candidate (docker compose with the store + the owning service on that Spring profile). Warm it up.
2. Drive a FIXED, SEEDED workload with the generator (same seed, same count e.g. 10,000, same wave pattern) via the `/generate` FEED flow. The store is the ONLY variable.
3. Measure:
   - **Ingest throughput** — events/sec accepted at the gateway.
   - **End-to-end lag** — time until event-store has persisted all N (poll its count/health, or wait for Kafka consumer lag → 0).
   - **Query latency under load** — repeated `GET /v1/stats/summary` (aggregation) and `GET /v1/events/samples` (filtered, paged): p50 / p95 / p99 over K requests. Use `hey`/`wrk` if available, else a `curl -w "%{time_total}"` loop.
   - Optional: `docker stats` CPU/memory during the run.
4. Tear down; repeat for the next candidate with the IDENTICAL workload.

## Output
Write a comparison report to `docs/storage-benchmark.md` (tracked — it feeds the README storage justification and `docs/03-sdd.md` §7): a table of candidate × { ingest throughput, end-to-end lag, stats p95, samples p95, CPU/mem } plus a recommendation and the tradeoffs. Record the fixed parameters (seed, count, query set, hardware) so the run is reproducible.

## Scaling sweep (find the sweet spots)
Only meaningful once a SHARED store exists — in-memory adapters are per-instance, so scaling a stateful service on in-memory is incorrect (fragmented offender window / event data), not just slow. Prerequisites before sweeping replicas:
1. **Shared state:** enrichment on the Redis offender window + shared dedup; event-store on Mongo. gateway (stateless) and analytics (reads shared store) scale cleanly.
2. **Kafka partitions:** raise the topic partition count (e.g. 12) — a consumer group parallelizes only up to the partition count; extra replicas beyond partitions sit idle. `events.raw` is keyed by clientIp and `events.enriched` by configId, so partitioning preserves per-key order.
3. **Ports:** drop the fixed host-port mapping on scaled Kafka-consumer services (enrichment, event-store); front gateway/analytics with a load balancer if scaling them.

Sweep matrix: **backend × replicas × partitions**. For each cell, drive the fixed seeded load and record throughput (events/sec), end-to-end lag, and query p95. Find the **knee** — where adding replicas stops improving throughput because the bottleneck moved to partition count or the store. Parameterize replicas via `deploy.replicas` / `docker compose up --scale <svc>=N` and partitions via the topic-creation config.

Report the knee per backend in `docs/storage-benchmark.md`: "throughput plateaus at N enrichment replicas / P partitions on <backend>, bottleneck = <partitions|store|CPU>."

## Notes
- Fairness: identical seeded dataset + identical queries + warm caches + ≥3 runs (report medians / p95). State caveats (single-host, laptop-class — real scale-out numbers need real hardware) honestly.
- Reuse the real adapters behind the `EventStore` port — never a throwaway benchmark path, so the numbers reflect production code.
- ClickHouse stays a words-only scaling narrative unless an adapter is actually built.
