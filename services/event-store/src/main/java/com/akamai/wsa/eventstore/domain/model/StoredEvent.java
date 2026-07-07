package com.akamai.wsa.eventstore.domain.model;

import com.akamai.wsa.contracts.Action;

import java.time.Instant;

public record StoredEvent(
        String eventId,
        Instant timestamp,
        int configId,
        String policyId,
        String clientIp,
        String hostname,
        String path,
        String method,
        int statusCode,
        String userAgent,
        StoredRule rule,
        Action action,
        StoredGeoLocation geoLocation,
        long requestSize,
        long responseSize,
        String attackType,
        int threatScore,
        Instant receivedAt
) {
    public StoredEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
    }
}
