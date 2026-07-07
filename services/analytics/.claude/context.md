# analytics — Service Context

**Role:** the **read side** of Mini WSA. Serves the analytics APIs directly:
- `GET /v1/stats/summary?configId=&from=&to=` — totals, byCategory (count + avgThreatScore), byAction counts, top-10 attackers, top-10 targeted paths.
- `GET /v1/events/samples?configId=&from=&to=&category=&action=&limit=&offset=` — filtered, `timestamp`-DESC, paginated (default 20 / max 100), with `total`.

**Port:** `:8084`. **No Kafka, no writes.**

**Data source:** in production, a **Mongo replica** of `event-store`'s enriched-event collection (SDD v2 §5.2, §7 — accepted shared-schema coupling). For dry runs / sanity, the **default runnable profile uses a seeded in-memory read store** (no external store required). Mongo is opt-in via the `mongo` profile and is **deferred to the DB-candidate phase**.

**Architecture:** hexagonal — domain-owned `AnalyticsReadStore` port; in-memory adapter is the default bean, Mongo adapter behind `@Profile("mongo")` later. Domain imports no Spring/Mongo. SDD refs: §5.2 (read path), §7 (storage/replica), §8 (API surface).

**Conventions:** records, immutability, FULL parameter names (no `v`/`e`), the assignment's **exact JSON shapes** (no `{success,data}` envelope), one-decimal averages, category-name string keys, `clientIp` as string. Milestone `v0.5-analytics`.

**Optional bonuses owned here:** B3 time-series endpoint; B4 rate-limiting (429 on these endpoints). Both optional — see `docs/superpowers/plans/2026-07-07-bonuses.md`.

## SHARED CONTRACTS
Before implementing, read **`services/contracts/.claude/context.md`** (the authoritative message API + shared enums). analytics does **not** consume Kafka, so it only depends on the shared **enums** (`AttackCategory`, `Action`, `Severity`) and — indirectly — on `event-store`'s Mongo document shape. **Any contract change is logged in `services/contracts/.claude/CHANGELOG.md`; on any entry there, re-check this plan.** The event-store document schema is a forward dependency to confirm when the Mongo profile (Task 5) is built.
