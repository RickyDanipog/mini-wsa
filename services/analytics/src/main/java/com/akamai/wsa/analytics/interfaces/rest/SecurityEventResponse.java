package com.akamai.wsa.analytics.interfaces.rest;

import com.akamai.wsa.analytics.domain.model.EnrichedEventView;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;

import java.time.Instant;

public record SecurityEventResponse(
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
        RuleResponse rule,
        Action action,
        GeoLocationResponse geoLocation,
        long requestSize,
        long responseSize,
        String attackType,
        int threatScore,
        Instant receivedAt) {

    public record RuleResponse(String id, String name, String message, Severity severity, AttackCategory category) {
    }

    public record GeoLocationResponse(String country, String city) {
    }

    public static SecurityEventResponse from(EnrichedEventView event) {
        return new SecurityEventResponse(
                event.eventId(),
                event.timestamp(),
                event.configId(),
                event.policyId(),
                event.clientIp(),
                event.hostname(),
                event.path(),
                event.method(),
                event.statusCode(),
                event.userAgent(),
                new RuleResponse(event.ruleId(), event.ruleName(), event.ruleMessage(),
                        event.severity(), event.category()),
                event.action(),
                new GeoLocationResponse(event.country(), event.city()),
                event.requestSize(),
                event.responseSize(),
                event.attackType(),
                event.threatScore(),
                event.receivedAt());
    }
}
