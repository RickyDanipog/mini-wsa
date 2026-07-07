package com.akamai.wsa.gateway.interfaces.rest.dto;

import com.akamai.wsa.contracts.Action;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Inbound ingest request for a single security event. Bean Validation guards
 * the required fields, enum binding and ISO-8601 timestamp at the boundary.
 */
public record IngestEventRequest(
        @NotBlank String eventId,
        @NotNull Instant timestamp,
        int configId,
        String policyId,
        @NotBlank String clientIp,
        String hostname,
        @NotBlank String path,
        String method,
        int statusCode,
        String userAgent,
        @Valid @NotNull RuleDto rule,
        @NotNull Action action,
        @Valid GeoLocationDto geoLocation,
        long requestSize,
        long responseSize
) {
}
