# Generate sample events

Run this from the repository root to send a small dataset to the local stack:

```bash
java scripts/GenerateEvents.java --count 200 --waves 3 \
  --post http://localhost:8080/v1/events/ingest
```

Use `--output events.json` in place of `--post` to write a JSON array. Change
`--seed` to add a distinct dataset to a database that already contains the
default dataset.
