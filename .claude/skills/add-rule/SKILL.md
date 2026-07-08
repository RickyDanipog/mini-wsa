---
name: add-rule
description: Add (or edit) a scoring rule on the enrichment rule engine and demonstrate its effect — POST the rule to /v1/rules, ingest one event crafted to match it, and show the threat score rose by the rule's points, proving rules apply live with no restart. Use when the user wants to add/change a scoring rule, demo the data-driven rule engine, or show how a rule affects new events.
---

# /add-rule — add a scoring rule and prove it works

Adds a rule to the enrichment scoring engine and demonstrates the before/after effect on a fresh event. The engine re-reads enabled rules on **every** event, so a new rule applies to the next event with no restart.

## Preconditions
- Stack up (`curl -fs localhost:8082/actuator/health`). If down, invoke **`deploy`** first.
- Endpoints: enrichment rules `:8082/v1/rules`, gateway ingest `:8081/v1/events/ingest`, analytics samples `:8084/v1/events/samples` (all also reachable same-origin via `localhost:8090/api/{enrichment,gateway,analytics}/…`).

## Rule shape
`GET :8082/v1/rules/options` returns the valid `operator` and `factKey` values. A rule is:
```json
{ "id": "geo-cn", "title": "China origin",
  "factKey": "geoLocation.country", "operator": "EQUAL_TO", "operand": "CN",
  "output": 50, "priority": 50, "enabled": true }
```
- `factKey` — dotted path into the event: `rule.severity`, `rule.category`, `action`, `path`, `method`, `statusCode`, `clientIp`, `geoLocation.country`, `offenderEventCount`.
- `operator` — one of the enum (`EQUAL_TO`, `IN`, `CONTAINS_ANY`, `GREATER_THAN`, `BETWEEN`, `EXISTS`, …). Match arity: `BETWEEN` needs `"lo,hi"`, `IN`/`CONTAINS_ANY` a comma list.
- `output` — points added when it matches (summed across matching rules, capped at 100).
- Blank/omitted `id` → the server generates one. Reusing an existing `id` **overwrites** that rule.

## Steps
1. **Parse intent** into a rule. Confirm the `factKey`/`operator`/`operand`/`output` with the user if ambiguous; validate the operator against `/v1/rules/options`.
2. **Add the rule** (create or edit):
   ```
   curl -s -X POST localhost:8082/v1/rules -H 'Content-Type: application/json' -d '<rule json>'
   ```
   Expect `201` (or `PUT /v1/rules/{id}` → `200` to edit). Invalid operator/blank field → `400 {error,details}`.
3. **Demonstrate the effect.** Craft one event that matches the rule and is otherwise minimal, stamped at **now** (so it sorts to the top of `samples`, which is `timestamp DESC`), with a fresh unique `eventId` and a fresh `clientIp` (to avoid the repeat-offender bonus muddying the number). POST it to the gateway. To show the delta cleanly, optionally ingest one identical event that does *not* match (or ingest before adding the rule) as a baseline.
4. **Read back the score.** Poll `:8084/v1/events/samples?limit=100` for the eventId (allow ~2–6s for the async pipeline) and report its `threatScore`. Confirm it equals the baseline score **plus the rule's `output`** (capped at 100).
5. **Report**: the rule created, the matching event, baseline → new score, and that it took effect with no restart. Mention the user can see/edit it in the Simulation Console's Scoring-rules panel (`localhost:8090`).

## Notes
- This adds real state. If the user was just demoing, offer to `DELETE :8082/v1/rules/{id}` afterward.
- With `wsa.rules=postgres` (the compose default) the rule persists across restarts; with `inmemory` it lasts only for the enrichment process lifetime.
- Some operators fail silently if misconfigured (e.g. `BETWEEN` with the wrong token count, an invalid `REGEX_MATCH` pattern) — if the score doesn't move, re-check operand arity before assuming a pipeline problem.
