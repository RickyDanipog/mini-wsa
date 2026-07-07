# Analytics Service — Plan Reconciliation

> Read-only reconciliation of `docs/superpowers/plans/2026-07-07-service-analytics.md`
> (authoritative) against SDD v2 (`docs/03-sdd.md` §5.2, §7, §8), the assignment
> (Parts 3 & 4 + bonuses B3/B4), and the current `services/analytics/` scaffold on
> `feature/v2-restructure`. Date: 2026-07-07.

## What aligns

- **Read-only, no Kafka, Mongo replica** — matches SDD §5.2/§7. Service owns no write model.
- **Endpoints served directly** (`GET /v1/stats/summary`, `GET /v1/events/samples`) — gateway is write-path only (SDD §8). ✓
- **Assignment Part 3 fields** all covered: `totalEvents`, `byCategory{count, avgThreatScore}`, `byAction` counts, `topAttackers` top-10 (`clientIp`,`count`,`avgThreatScore`), `topTargetedPaths` top-10 (`path`,`count`); `configId` optional → all; `from`/`to` → `TimeRange.unbounded()`. Averages rendered to one decimal.
- **Assignment Part 4** covered: optional `configId`/`from`/`to`/`category`/`action`, `limit` default 20 / max 100 (`clampLimit`), `offset ≥ 0`, `timestamp` DESC, `total` present. Invalid enum → 400.
- **Exact JSON shapes, no `{success,data}` envelope**; `ClientIp`→string; category-name string keys. ✓
- **Hexagonal**: domain-owned `AnalyticsReadStore` port, in-memory + Mongo adapters, `contracts` enums, pure domain. Matches AGENTS §3/§4.
- **In-memory reference adapter exists** for fast tests (aligns with the in-memory-first intent — see the caveat under Conflicts).
- Milestone `v0.5-analytics`, TDD structure, full parameter names. ✓

## Missing coverage

- **B4 rate limiting (429 on stats/samples)** — NOT in this service plan; it lives in `2026-07-07-bonuses.md` as a `RateLimitFilter` scoped to `/v1/stats/**` and `/v1/events/samples`. Since those endpoints are owned here, B4 (if chosen) is applied in *this* service. Optional bonus — flag, don't block.
- **B3 time-series** — present but correctly marked OPTIONAL (Task 5). No gap.
- No functional Part 3/4 field is uncovered.

## Conflicts

1. **In-memory vs Mongo as the runtime default (the important one).** The plan (Task 4) makes the **Mongo adapter primary** and treats the in-memory adapter as **test-only**. The user's standing directive is to **run on cached/in-memory for dry runs & sanity, and defer real DBs until the logic is perfected.** So the runtime default conflicts with that directive. → The in-memory read store should be a *runnable* (profile-gated) bean so `:8084` can be dry-run with no Mongo; Mongo becomes an opt-in profile for the later DB phase.
2. **No hard contradictions** with the assignment's response shapes, pagination caps, sort order, or top-N — those all match.

## Stale assumptions / dependencies to track

- **Data-source tension for a pure in-memory dry run.** In v2, analytics gets data from a **Mongo replica of event-store**, not from a stream. With no Mongo, the in-memory read store has **no data source** — it must be **seeded** (a dev-profile seed or a fixture loader) to be meaningfully dry-run in isolation. This is inherent to the distributed split (unlike gateway/enrichment/event-store, whose in-memory-first is self-contained). Not a plan error, but call it out: "in-memory-first" for analytics = *seeded* in-memory read store for dev.
- **Schema coupling with event-store (accepted, SDD §7).** Task 4's `EnrichedEventDocument` (`@Document("events")`) must match event-store's document shape (`_id = eventId`, field names, indexes) — event-store isn't built yet, so this mapping is a forward dependency to confirm when both land.
- The superseded `part3-stats.md` / `part4-samples.md` assume the monolith's single `EventStore.summarize/findSamples`; they are logic references only — the read-model-over-replica design here is authoritative.

## Recommended next steps (in-memory first)

1. **Task 1 as-is** — read model, query/result records, `AnalyticsReadStore` port, in-memory adapter + unit tests. Pure, no infra. ✅ first.
2. **Adjust Task 4 gating** — make the **in-memory read store the default runnable bean** (e.g. `@Profile("!mongo")` + a small dev seed), and put the **Mongo adapter behind a `mongo` profile**. Lets `:8084` be dry-run/sanity-checked with seeded in-memory data now.
3. **Tasks 2–3** — stats + samples use cases and controllers against the port; verify with the in-memory adapter + `@WebMvcTest`. Fully achievable with zero DB.
4. **Defer Task 4 (Mongo + Testcontainers) to the DB phase**, per the user's directive — implement when the logic is perfected and we compare DB candidates.
5. Keep **B3 (time-series)** and **B4 (rate-limit)** as optional; if B4 is chosen, implement its filter in this service.
6. Confirm the **event-store document schema** before writing the Mongo read mapping (cross-service coupling).
