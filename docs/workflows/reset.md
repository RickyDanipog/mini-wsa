# Reset the local data volumes

Tears everything down **including data volumes** (so Postgres events, seeded rules, Redis offender windows, and Kafka offsets are all wiped), then brings the stack back up clean.

## ⚠️ Destructive — confirm first
This deletes **all ingested events and any custom scoring rules** added at runtime (the seeded 8 defaults come back; user-added rules do not). If the user only wants to stop the stack without losing data, use `docker compose stop` instead and do **not** run this. Confirm the user wants data wiped before proceeding.

## Steps
1. **Tear down + wipe volumes** from the repo root:
   ```
   docker compose down -v
   ```
2. **Bring it back up.** Follow [Start the stack and check its health](deploy.md) (it rebuilds if needed, waits for health on `:8081-:8084` and the console on `:8090`, and smoke-tests). Do not hand-roll `compose up` — reuse `deploy` so health/console checks stay in one place.
3. **Reseed (optional, ask unless the user already said so).** Follow [Generate events and attack waves](generate.md) for a starting dataset, e.g. `--wsa.generator.total-events=200 --wsa.generator.wave-count=5` (mode `HTTP` against the running gateway). Skip if host JDK 21 + Maven are absent (note it — the user can seed from the console's "Run attack wave" button instead).
4. **Report** the clean state: services up, data volume fresh, whether it was reseeded, and the console URL `http://localhost:8090`.

## Notes
- Scope: `docker compose down -v` removes the named volumes for this project only.
- Because eventIds from the generator are deterministic (`evt-00000000…`), reseeding after a full wipe lands the whole range cleanly (nothing to dedup against).
- Redis and Kafka state are wiped too, so the repeat-offender windows and consumer offsets start empty — the first events after a reset will not carry any stale offender history.
