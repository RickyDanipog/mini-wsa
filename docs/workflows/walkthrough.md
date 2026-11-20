# Verify Mini WSA

1. Follow [the deploy guide](deploy.md) and read the current total from
   `GET /v1/stats/summary`.
2. Follow [the generator guide](generate.md) with a new seed and verify the
   total rises immediately by the number sent.
3. Query `GET /v1/events/samples?category=INJECTION&limit=5`. Verify the
   returned events have `attackType`, `threatScore`, and `receivedAt`.
4. Query `GET /v1/stats/timeseries?interval=5m` and verify the bucket counts.
5. Run the alert definition and evaluation examples in the
   [README](../../README.md#api) and inspect the resulting rule and count.
6. Run `mvn clean verify` to check the scoring matrix, validation, pagination,
   repeated and concurrent ingestion, alerts, and rate limiting.

Report the checks you ran and their observed results.
