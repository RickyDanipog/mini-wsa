package com.akamai.wsa.analytics.infrastructure.persistence.mongo;

import com.akamai.wsa.analytics.domain.model.EnrichedEventView;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "events")
public record EnrichedEventDocument(
        @Id String id,
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
        RuleDocument rule,
        Action action,
        GeoLocationDocument geoLocation,
        long requestSize,
        long responseSize,
        String attackType,
        int threatScore,
        Instant receivedAt) {

    public record RuleDocument(String id, String name, String message, Severity severity, AttackCategory category) {
    }

    public record GeoLocationDocument(String country, String city) {
    }

    public EnrichedEventView toView() {
        return new EnrichedEventView(
                eventId != null ? eventId : id,
                timestamp,
                configId,
                policyId,
                clientIp,
                hostname,
                path,
                method,
                statusCode,
                userAgent,
                rule != null ? rule.id() : null,
                rule != null ? rule.name() : null,
                rule != null ? rule.message() : null,
                rule != null ? rule.severity() : null,
                rule != null ? rule.category() : null,
                action,
                geoLocation != null ? geoLocation.country() : null,
                geoLocation != null ? geoLocation.city() : null,
                requestSize,
                responseSize,
                attackType,
                threatScore,
                receivedAt);
    }
}
