# Storage Benchmark — Mongo vs Postgres

**100,000 events · 3 runs per backend (medians reported) · plus a partition × replica scaling sweep.**
Single-host, laptop-class (macOS, Docker Engine 29.6.1, JDK 21). Absolute numbers
are machine-bound — read the **ratios and the shape**, not the raw figures.

## Method (fair by construction)

- **Workload:** event-generator, seed `42`, `total-events=100000`, `wave-count=5`, `batch-size=200`, `output-mode=HTTP`. Identical dataset every run.
- **Pipeline:** gateway → Kafka → enrichment (Redis offender window) → event-store → shared store; analytics reads the same store. **Only** the event-store/analytics backend changes (`WSA_STORAGE=mongo` vs `postgres`); everything else is held constant.
- **Store reset** between every run (`docker compose down`, no volumes → cold store each time).
- **Metrics:** *drain* = feed-done → all 100k persisted (store write throughput); *end-to-end* = feed-start → all persisted; query latency via `ab -n 300 -c 10` on `/v1/stats/summary` (the 4-way aggregation) and `/v1/events/samples?limit=20`.
- Reused the **real adapters** behind the ports — no throwaway benchmark path.

## Part A — 100k baseline, single instance (medians of 3 runs)

| Metric | Mongo | Postgres | Advantage |
|--------|------:|---------:|:---------:|
| Persist rate (drain) | 1,104 ev/s | **1,302 ev/s** | PG **1.18×** |
| Drain time (100k) | 90.6 s | **76.8 s** | PG |
| End-to-end (100k) | ~101 s | **~87 s** | PG |
| **stats** p50 | 1,293 ms | **289 ms** | PG |
| **stats** p95 | 1,468 ms | **431 ms** | PG **3.4×** |
| **stats** p99 | 1,625 ms | **492 ms** | PG |
| **samples** p95 | 106 ms | **36 ms** | PG **2.9×** |

Ingest→gateway accept was ~9–10k ev/s on both (store-independent — the gateway
only validates and publishes to Kafka), confirming the store drain, not ingest,
is the write-side bottleneck.

**Reading:** Postgres wins every axis, decisively on the **analytics hot path**.
At 100k the stats aggregation costs Mongo a **1.47 s p95**; the same four grouped
summaries over an indexed Postgres table run at **431 ms p95** (3.4× faster). The
write advantage is smaller but real (~18%). This **overturns the earlier
qualitative assumption** that Mongo would lead on aggregation — the reason we
benchmarked instead of guessing.

## Part B — scaling sweep (Postgres, the Part A winner)

Kafka partitions × (enrichment + event-store) replicas. Analytics stays single-instance.

| partitions | replicas | Persist rate | Drain | stats p95 | Note |
|-----------:|---------:|-------------:|------:|----------:|------|
| 1 | 1 | 1,302 ev/s | 76.8 s | 431 ms | baseline (Part A median) |
| 4 | 1 | 1,344 ev/s | 74.4 s | 412 ms | +partitions, **1** consumer → no gain |
| 4 | 4 | **2,211 ev/s** | 45.2 s | 410 ms | replicas matched to partitions → **1.7×** |
| 8 | 4 | 2,212 ev/s | 45.2 s | 398 ms | extra partitions idle (replicas cap at 4) |

**The knee:** write throughput plateaus at **~2,200 ev/s at 4 replicas / 4
partitions** on Postgres. The two controls behave exactly as the model predicts:

- **Partitions without consumers do nothing** (`p4/r1` ≈ `p1/r1`) — a consumer group parallelizes only up to the number of consumers.
- **Replicas matched to partitions give the gain** (`p4/r4` = 1.7× baseline).
- **Partitions beyond replicas are idle** (`p8/r4` = `p4/r4`) — 4 consumers cover only 4 of 8 partitions.

The 1→4 scaling is **sub-linear (1.7×, not 4×)**, and the `r4` cells show the feed
phase itself slowing (10.5 s → 17.7 s) from CPU contention. On a single host,
**the bottleneck past ~4 replicas is host CPU**, not partitions or the store —
running 4 enrichment + 4 event-store JVMs saturates the machine. Real scale-out
numbers need multiple hosts; the *shape* (linear-ish until CPU-bound, then flat)
is the transferable result.

## Recommendation

**Postgres** for the event store + analytics read model; **Redis** stays for the
enrichment offender window (orthogonal — not part of this comparison). For this
**aggregation-heavy analytics workload** at the tested scale, a relational store
with `(config_id, timestamp)` / `(client_ip, timestamp)` indexes beats Mongo on
the query path by ~3× and on writes by ~18%, and it scales cleanly with Kafka
partitions until the host CPU caps it.

Mongo's counter-arguments (schema flexibility, native horizontal sharding at very
large scale) are real but don't pay off at this scale for this query shape. Next
structural step per the storage plan: keep Postgres on `main`, preserve the Mongo
adapter on a branch/tag for history, and trim its dependency from the shipped
services.

## Caveats (honest)

- Single host, laptop-class — absolute throughput is machine-bound; trust ratios.
- 3 runs, medians reported; run-to-run spread was tight (drain within ~6%, stats p95 within ~5%).
- Scaling sweep is 1 run per cell (directional) and CPU-bound past ~4 replicas on one machine.
- Analytics was not scaled (single reader); query latency held flat across the sweep, as expected.
