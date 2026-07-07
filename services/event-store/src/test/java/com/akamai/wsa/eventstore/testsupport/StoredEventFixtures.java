package com.akamai.wsa.eventstore.testsupport;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;
import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import com.akamai.wsa.eventstore.domain.model.StoredGeoLocation;
import com.akamai.wsa.eventstore.domain.model.StoredRule;

import java.time.Instant;

public final class StoredEventFixtures {

    private StoredEventFixtures() {
    }

    public static StoredEvent storedEvent(String eventId, int configId) {
        return new StoredEvent(
                eventId,
                Instant.parse("2026-05-20T14:32:10Z"),
                configId,
                "pol_web1",
                "203.0.113.42",
                "www.example.com",
                "/api/v1/login",
                "POST",
                403,
                "Mozilla/5.0",
                new StoredRule("950001", "SQL_INJECTION", "SQL Injection Attack Detected",
                        Severity.CRITICAL, AttackCategory.INJECTION),
                Action.DENY,
                new StoredGeoLocation("CN", "Beijing"),
                1024,
                256,
                "SQL/Command Injection",
                75,
                Instant.parse("2026-05-20T14:32:10.512Z"));
    }
}
