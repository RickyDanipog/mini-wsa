package com.akamai.wsa.contracts;

import java.time.Instant;

/**
 * An enriched event as it leaves enrichment on the {@code events.enriched} topic:
 * the raw event plus the three enrichment fields. {@code attackType} carries the
 * human-readable display name (e.g. "SQL/Command Injection").
 */
public record EnrichedEventMessage(
        RawEventMessage rawEvent,
        String attackType,
        int threatScore,
        Instant receivedAt
) {
}
