---
name: deploy
description: Bring up the full Mini WSA cluster locally with Docker Compose — verify prerequisites (run the prepare skill if anything is missing), build and start Kafka + Postgres + Redis + the four services, wait for health, and run a smoke test. Use to deploy/start the environment or when the user asks to run the stack.
---

# /deploy — bring up the Mini WSA cluster

Starts the whole pipeline (Kafka + Postgres + Redis + gateway/enrichment/event-store/analytics) via `docker compose`. Images build in-container (multi-stage), so **Docker is the only prerequisite to run the cluster**.

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
3. **Wait for health** — poll each service until it returns 200:
   ```
   for port in 8081 8082 8083 8084; do
     until curl -fs "localhost:$port/actuator/health" >/dev/null; do sleep 2; done
   done
   docker compose ps
   ```
4. **Smoke test.**
   - `curl -s localhost:8081/v1/ping` → `{"status":"ok","service":"gateway"}`
   - Seed a little data (needs JDK 21 + Maven on the host): drive the **`generate`** skill with a small count, e.g. `--wsa.generator.total-events=200`. If host JDK/Maven are absent, note it and skip this step — the cluster is still up.
   - `curl -s "localhost:8084/v1/stats/summary" | jq` and `curl -s "localhost:8084/v1/events/samples?limit=3" | jq` to confirm the ingest→enrich→store→analytics loop is closed.
5. **Report** the running services + ports, and how to stop:
   - stop, keep data: `docker compose stop`
   - tear down: `docker compose down` (add `-v` to wipe volumes/data)

## Notes
- Default storage: enrichment on **Redis**, event-store + analytics on **PostgreSQL**.
- Ports: gateway 8081, enrichment 8082, event-store 8083, analytics 8084; infra: Kafka 9092, Postgres 5432, Redis 6379.
- Running the cluster needs only Docker; host JDK 21 + Maven are only for the generator and the test suite (`/prepare` installs them).
