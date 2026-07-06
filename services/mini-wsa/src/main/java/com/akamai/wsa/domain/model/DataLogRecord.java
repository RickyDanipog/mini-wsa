package com.akamai.wsa.domain.model;

import java.time.Instant;

/**
 * A raw, validated security event as received from the edge (the "DLR").
 * Holds all original fields; enrichment ({@link AttackType}, {@link ThreatScore},
 * receivedAt) is layered on top in {@link SecurityEvent}.
 */
public record DataLogRecord(
        String eventId,
        Instant timestamp,
        int configId,
        String policyId,
        ClientIp clientIp,
        String hostname,
        String path,
        String method,
        int statusCode,
        String userAgent,
        Rule rule,
        Action action,
        GeoLocation geoLocation,
        long requestSize,
        long responseSize
) {
    public DataLogRecord {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        if (clientIp == null) {
            throw new IllegalArgumentException("clientIp must not be null");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (rule == null) {
            throw new IllegalArgumentException("rule must not be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
    }
}
