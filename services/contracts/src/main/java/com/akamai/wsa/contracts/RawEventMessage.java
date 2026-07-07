package com.akamai.wsa.contracts;

import java.time.Instant;

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
