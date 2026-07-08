#!/usr/bin/env bash
#
# produce-to-kafka.sh — publish generated security events to the gateway's Kafka
# ingestion topic (B2 Streaming Ingestion).
#
# Prerequisites:
#   - The Mini WSA stack is up (docker compose up --build) so the wsa-kafka
#     container and the gateway (subscribed to events.ingest) are running.
#   - JDK 21 + Maven on the host (used to run the event-generator module).
#   - jq (used to split the generated JSON array into one object per line).
#
# What it does:
#   1. Runs the event-generator in JSON_FILE mode to a temp file (a JSON array).
#   2. Streams that array as one JSON object per line into the Kafka console
#      producer inside the wsa-kafka container. Each line becomes one
#      events.ingest message, which the gateway's EventIngestListener validates,
#      maps, and republishes to events.raw — the same path as POST /v1/events/ingest.
#
# Usage:
#   scripts/produce-to-kafka.sh [count=200] [topic=events.ingest]

set -euo pipefail

COUNT="${1:-200}"
TOPIC="${2:-events.ingest}"
KAFKA_CONTAINER="wsa-kafka"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

for tool in mvn jq docker; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "error: '$tool' is required but not installed" >&2
    exit 1
  fi
done

OUT="$(mktemp -t wsa-events.XXXXXX.json)"
trap 'rm -f "$OUT"' EXIT

echo "Generating $COUNT events to $OUT ..."
mvn -q -f "$REPO_ROOT/pom.xml" -pl services/event-generator spring-boot:run \
  -Dspring-boot.run.arguments="--wsa.generator.total-events=$COUNT --wsa.generator.output-mode=JSON_FILE --wsa.generator.output-file=$OUT"

echo "Publishing events to topic '$TOPIC' via $KAFKA_CONTAINER ..."
jq -c '.[]' "$OUT" | docker exec -i "$KAFKA_CONTAINER" \
  /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$TOPIC"

echo "Done. Published $(jq 'length' "$OUT") events to '$TOPIC'."
