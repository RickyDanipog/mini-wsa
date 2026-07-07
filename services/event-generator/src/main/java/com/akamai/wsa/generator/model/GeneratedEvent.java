package com.akamai.wsa.generator.model;

import java.time.Instant;

public record GeneratedEvent(
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
        GeneratedRule rule,
        String action,
        GeneratedGeoLocation geoLocation,
        long requestSize,
        long responseSize
) {
}
