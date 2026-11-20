package com.akamai.wsa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

record SecurityEvent(
        @NotBlank String eventId,
        @NotNull Instant timestamp,
        @NotNull Integer configId,
        String policyId,
        @NotBlank String clientIp,
        String hostname,
        @NotBlank String path,
        String method,
        Integer statusCode,
        String userAgent,
        @Valid @NotNull Rule rule,
        @NotNull Action action,
        GeoLocation geoLocation,
        Long requestSize,
        Long responseSize) {

    record Rule(@NotBlank String id, @NotBlank String name, String message,
                @NotNull Severity severity, @NotNull Category category) {}

    record GeoLocation(String country, String city) {}

    enum Severity { CRITICAL, HIGH, MEDIUM, LOW }

    enum Action { DENY, ALERT, MONITOR }

    enum Category {
        INJECTION("SQL/Command Injection"),
        XSS("Cross-Site Scripting"),
        PROTOCOL_VIOLATION("Protocol Anomaly"),
        DATA_LEAKAGE("Data Exfiltration"),
        BOT("Bot Activity"),
        DOS("Denial of Service"),
        RATE_LIMIT("Rate Limiting");

        final String attackType;

        Category(String attackType) {
            this.attackType = attackType;
        }
    }
}
