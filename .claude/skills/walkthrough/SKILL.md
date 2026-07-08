---
name: walkthrough
description: Exercise every Mini WSA use case end-to-end against a running stack and print a pass/fail checklist — the required parts (FR1 ingest + validation, FR2 classification/scoring incl. repeat-offender, FR3 stats, FR4 samples pagination/filters, FR5 generator) and the bonuses (B1 alerts, B2 Kafka ingestion, B3 time-series, B4 rate limit) plus scoring-rule CRUD. Use when the user wants to verify the system works, demo the features headlessly, or confirm the submission is complete before shipping.
---

# /walkthrough — prove every feature works

Drives the **running** stack through each requirement and reports a checklist. This is the headless/curl counterpart to the Simulation Console (`:8090`); use it to confirm the whole assignment is satisfied.

## Preconditions
- The stack must be up. Check `curl -fs localhost:8081/actuator/health` (and `:8082/:8083/:8084`). If any is down, **invoke the `deploy` skill first**, then continue.
- `jq` and `python3` are used for assertions. `B2` and `FR5` also need JDK 21 + Maven on the host (via `/prepare`); if absent, skip those two and say so.

## How to run
Go feature by feature. For each, run the commands, compare to the expected result, and record ✅/❌ with the observed value. Use fresh, unique `eventId`s (e.g. suffix with `$(date +%s)`) and stamp events at **now** (`timestamp` = current UTC) so they sort to the top of `samples` (which is `ORDER BY timestamp DESC`). Allow ~2–6s for the async pipeline (gateway→Kafka→enrichment→Kafka→event-store→analytics) before asserting reads.

1. **FR1 — Ingestion & validation** (gateway `:8081`)
   - single valid event → `201`; batch array → `201`.
   - invalid event (blank `clientIp`, bad enum) → `400` with `error.code=VALIDATION_FAILED` and per-field `details[]`.
2. **FR2 — Classification & scoring** (assert via analytics samples)
   - ingest `severity=CRITICAL, action=DENY, path=/admin` → `attackType="SQL/Command Injection"`, `threatScore=75` (40+20+15).
   - repeat-offender: send 7 events from one IP on `/admin` CRITICAL/DENY → the 6th and 7th reach `90` (75+15); earlier ones stay `75`.
3. **FR3 — Stats** (`GET :8084/v1/stats/summary`) — response has `totalEvents`, `byCategory` (count + `avgThreatScore`), `byAction`, `topAttackers` (≤10), `topTargetedPaths` (≤10). Repeat with `?configId=&from=&to=`.
4. **FR4 — Samples** (`GET :8084/v1/events/samples`) — `limit=5&offset=0` vs `offset=5` are disjoint; results sorted by `timestamp` desc; `total` present; `limit=500` clamps to `100`; `category`/`action` filters narrow results; bad enum → `400`.
5. **FR5 — Data generator** — drive the **`generate`** skill (or `mvn -pl services/event-generator spring-boot:run … --wsa.generator.output-mode=HTTP`) for e.g. 200 events; confirm `stats/summary.totalEvents` rises. Note: eventIds are deterministic `evt-00000000…`, so re-running the same range is a no-op (dedup by `eventId`) — bump the count or expect only novel ids to land.
6. **B1 — Alerting** — `POST :8084/v1/alerts/define {category,threshold,windowMinutes}` → `201`; `GET :8084/v1/alerts/evaluate` → rule with `firing:true` once the category count crosses the threshold; bad category → `400`.
7. **B2 — Kafka ingestion** — `scripts/produce-to-kafka.sh 20`; confirm `totalEvents` rises (novel ids). Skip if host JDK/Maven absent.
8. **B3 — Time-series** — `GET :8084/v1/stats/timeseries?interval=1m|5m|1h` → `buckets` non-empty and `interval` echoed; invalid interval → `400`.
9. **B4 — Rate limit** — fire ~120 rapid `GET :8084/v1/stats/summary`; expect a mix of `200` and `429` (default ~100/min per client IP).
10. **Scoring-rule CRUD** (enrichment `:8082`) — `GET /v1/rules` lists 8 defaults; `POST` a rule → `201`; `PUT /{id}` → `200`; `DELETE /{id}` → `204`; invalid operator → `400`. Optional: add a `geoLocation.country == XX → +50` rule, ingest a matching event, and show its score rose by 50 (rules apply on the next event, no restart).

## Output
Print a compact checklist — one line per item — with ✅/❌ and the observed value (e.g. `FR2 scoring ✅ score=75`). End with a one-line verdict: all required parts + which bonuses passed. If anything failed, show the failing command and response; do not silently pass.

## Notes
- Same-origin variant: every `:808x` endpoint is also reachable through the console proxy at `localhost:8090/api/{gateway,enrichment,analytics}/…`.
- This skill only *reads and exercises*; it never edits code. It leaves test data in the store — mention that, and that `docker compose down -v` resets it.
