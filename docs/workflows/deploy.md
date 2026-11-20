# Start Mini WSA

From the repository root, run:

```bash
docker compose up -d --build
docker compose ps
curl --retry 10 --retry-connrefused --retry-delay 1 -fsS http://localhost:8080/v1/stats/summary
```

The summary should return JSON with `totalEvents`. If the request fails, inspect
`docker compose logs --tail=100 app postgres`.

If port 8080 is in use, start with `WSA_PORT=8088 docker compose up -d --build`
and use port 8088 for the API checks.
