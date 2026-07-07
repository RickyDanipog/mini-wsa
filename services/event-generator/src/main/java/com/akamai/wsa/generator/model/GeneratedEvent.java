package com.akamai.wsa.generator.model;

import java.time.Instant;

/**
 * A generated security event. Field names and order intentionally mirror
 * {@code contracts.RawEventMessage} / the gateway's ingest request, so the
 * serialized JSON is accepted as-is (enum names carried as Strings).
 */
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
