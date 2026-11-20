# Start Mini WSA

From the repository root, run:

```bash
docker compose up -d --build
docker compose ps
curl --retry 10 --retry-connrefused --retry-delay 1 -fsS http://localhost:8080/v1/stats/summary
```

The summary should return JSON with `totalEvents`. If the request fails, inspect
`docker compose logs --tail=100 app postgres`.
