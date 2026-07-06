# Mini WSA — Effort Estimate

> Status: Draft for review · Date: 2026-07-06
> Basis: one competent backend dev, **AI-assisted**, targeting a **Strong submission**.
> Unit = **ideal focused engineering hours** (design + implement + test + doc-slice for that piece).
> Ranges reflect low/high; the high end assumes an unfamiliar storage engine or extra polish.

## How to read this
- "Ideal hours" ≠ calendar time. With breaks/context-switching, ~5–6 ideal hours ≈ one working day.
- The **repeat-offender check (FR2)** and **aggregation queries (FR3)** are the
  two items most sensitive to the storage choice — their high end can move once
  the stack is picked.
- Learning-curve time for tech you haven't used (e.g. ClickHouse, Kafka) is
  called out separately and **not** baked into the base numbers.

---

## Required scope

| # | Requirement | Low | High | Notes / risk |
|---|-------------|----:|-----:|--------------|
| S0 | **Project scaffolding & setup** — Spring Boot skeleton, config, storage wiring, base docker-compose, repo/CI-lite | 2 | 4 | One-time cost; higher if storage needs container plumbing. |
| S1 | **FR1 Ingestion API** — DTOs, single+batch, bean validation, enum/timestamp checks, 201/400 semantics, `receivedAt` | 3 | 5 | Batch partial-failure semantics adds an hour. |
| S2 | **FR2 Classification & Enrichment** — attackType map, threat-score matrix, **repeat-offender sliding window** | 4 | 6 | ⚠️ Hardest logic; the window query/perf is the interview centerpiece. |
| S3 | **FR3 Stats API** — byCategory (+avg), byAction, topAttackers, topTargetedPaths, time-range aggregation | 4 | 6 | ⚠️ Aggregation shape depends on storage; top-N + averages. |
| S4 | **FR4 Samples API** — optional filters, pagination (limit/offset, caps), sort desc, total count | 2 | 4 | Straightforward once storage query layer exists. |
| S5 | **FR5 Data Generator** — realistic events, attack-wave bursts, configurable N, feeder to ingest | 3 | 4 | Fun but easy to over-engineer; keep it scoped. |
| S6 | **Testing** — unit (scoring matrix + edges), ≥1 integration test w/ Testcontainers | 3 | 5 | Grader explicitly checks this; don't skimp. |
| S7 | **README + architecture diagram + docs** | 2 | 3 | Diagram + storage justification + improvements/challenges sections. |
| | **Required subtotal** | **23** | **37** | ≈ **4–6 working days** |

---

## Bonus scope (pick to fit budget; assignment asks for 1–2)

| # | Bonus | Low | High | Value-per-effort |
|---|-------|----:|-----:|------------------|
| B3 | **Time-series endpoint** (interval bucketing) | 2 | 3 | ★★★ Highest — reuses FR3 aggregation, strong dashboard story. |
| B4 | **Rate limiting** (429, per-IP/min) | 1 | 2 | ★★★ Cheapest cross-cutting win. |
| B1 | **Alerting** (define + evaluate rules) | 3 | 5 | ★★ Good stateful-logic depth, strong SOC narrative. |
| B2 | **Streaming ingestion (Kafka)** | 5 | 8 | ★ Most impressive scaling story, most infra + risk. |

---

## Learning-curve add-ons (only if the tech is new to you)
- ClickHouse (if chosen as storage): **+2–4h**
- Kafka (if B2 chosen): **+2–4h**
- Testcontainers (if new): **+1–2h**

---

## Bottom line (Strong submission)

| Package | Ideal hours | ≈ Working days |
|---------|------------:|---------------:|
| **Required only** | 23–37 | 4–6 |
| Required **+ B3 + B4** (recommended 2 cheapest, highest-value bonuses) | 26–42 | 4.5–7 |
| Required **+ B1 + B3** (best "depth" pairing) | 28–45 | 5–7.5 |
| **Everything** (all 4 bonuses incl. Kafka) | 34–55 | 6–9 |

**Recommendation:** target *Required + B3 (time-series) + B4 (rate limiting)*
for the best ratio of impact to effort, and keep B1 (alerting) as a stretch if
time allows. Treat B2 (Kafka) as the last thing to add — it's the biggest time
sink and the least differentiating relative to a clean, well-tested core.

> These numbers will be refined once the SDD locks the storage engine — that
> single decision is the main swing factor on S2 and S3.
