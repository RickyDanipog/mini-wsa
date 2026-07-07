package com.akamai.wsa.contracts;

import java.time.Instant;

/**
 * A validated, untransformed security event as it leaves the gateway on the
 * {@code events.raw} topic. Enrichment fields are not present yet.
 */
public record RawEventMessage(
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
        RuleMessage rule,
        Action action,
        GeoLocationMessage geoLocation,
        long requestSize,
        long responseSize
) {
}
