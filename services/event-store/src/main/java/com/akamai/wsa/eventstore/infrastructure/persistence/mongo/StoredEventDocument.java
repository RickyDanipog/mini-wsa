package com.akamai.wsa.eventstore.infrastructure.persistence.mongo;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.eventstore.domain.model.StoredGeoLocation;
import com.akamai.wsa.eventstore.domain.model.StoredRule;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "events")
public record StoredEventDocument(
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
        StoredRule rule,
        Action action,
        StoredGeoLocation geoLocation,
        long requestSize,
        long responseSize,
        String attackType,
        int threatScore,
        Instant receivedAt
) {
}
