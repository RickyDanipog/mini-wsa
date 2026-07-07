package com.akamai.wsa.analytics.domain.model;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;

import java.time.Instant;

public record EnrichedEventView(
        String eventId, Instant timestamp, int configId, String policyId, String clientIp,
        String hostname, String path, String method, int statusCode, String userAgent,
        String ruleId, String ruleName, String ruleMessage, Severity severity, AttackCategory category,
        Action action, String country, String city, long requestSize, long responseSize,
        String attackType, int threatScore, Instant receivedAt) {
}
