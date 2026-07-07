package com.akamai.wsa.contracts;

import java.time.Instant;

public record EnrichedEventMessage(
        RawEventMessage rawEvent,
        String attackType,
        int threatScore,
        Instant receivedAt
) {
}
