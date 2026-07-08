---
name: deploy
description: Bring up the full Mini WSA cluster locally with Docker Compose — verify prerequisites (run the prepare skill if anything is missing), build and start Kafka + Postgres + Redis + the four services + the Simulation Console (demo-ui), wait for health, run a smoke test, and point the user at the console at http://localhost:8090. Use to deploy/start the environment, run the demo, or when the user asks to run the stack.
---

# /deploy — bring up the Mini WSA cluster + Simulation Console

Starts the whole pipeline (Kafka + Postgres + Redis + gateway/enrichment/event-store/analytics) **and the Simulation Console** (`demo-ui`, nginx at `:8090`) via `docker compose`. Images build in-container (multi-stage), so **Docker is the only prerequisite to run the cluster**. The console is the fastest way to exercise every use case — no curl required.

## Steps
1. **Check prerequisites.** Confirm Docker is present and the daemon is up:
   ```
   docker info >/dev/null 2>&1 && docker compose version
   ```
   If Docker is missing or the daemon is down, **invoke the `prepare` skill** to install/guide it, then re-check. Do not proceed until `docker info` succeeds.
2. **Bring up the stack** from the repo root (first run builds the images — a few minutes):
   ```
   docker compose up --build -d
   ```
3. **Wait for health** — poll each service until it returns 200, then confirm the console serves:
   ```
   for port in 8081 8082 8083 8084; do
     until curl -fs "localhost:$port/actuator/health" >/dev/null; do sleep 2; done
   done
   until curl -fs localhost:8090/ >/dev/null; do sleep 2; done
   docker compose ps
   ```
4. **Smoke test.**
   - `curl -s localhost:8081/v1/ping` → `{"status":"ok","service":"gateway"}`
   - Seed a little data (needs JDK 21 + Maven on the host): drive the **`generate`** skill with a small count, e.g. `--wsa.generator.total-events=200`. If host JDK/Maven are absent, note it and skip this step — the cluster is still up (or seed from the console's "Run attack wave" button instead).
   - `curl -s "localhost:8084/v1/stats/summary" | jq` and `curl -s "localhost:8084/v1/events/samples?limit=3" | jq` to confirm the ingest→enrich→store→analytics loop is closed.
5. **Point the user at the Simulation Console: http://localhost:8090** — a single-page dashboard that drives every use case with no curl: ingest one/batch/invalid, run attack waves (with a configurable inter-wave interval), craft a custom event and read back its threat score, add/edit/delete scoring rules live, refresh stats / samples (paginated) / time-series, define & evaluate alerts, and trip the rate limiter. All calls go same-origin through the console's nginx proxy.
6. **Report** the running services + ports, the console URL, and how to stop:
   - stop, keep data: `docker compose stop`
   - tear down: `docker compose down` (add `-v` to wipe volumes/data)

## Notes
- Default storage: enrichment on **Redis**, event-store + analytics on **PostgreSQL**.
- Ports: gateway 8081, enrichment 8082, event-store 8083, analytics 8084, **Simulation Console 8090**; infra: Kafka 9092, Postgres 5432, Redis 6379.
- Running the cluster needs only Docker; host JDK 21 + Maven are only for the generator and the test suite (`/prepare` installs them).
- **nginx caches upstream IPs:** if you rebuild/recreate a backend service after the stack is up (e.g. `docker compose up -d --build enrichment`), the console can return `502` until you also `docker compose restart demo-ui` so nginx re-resolves the new container IPs.
