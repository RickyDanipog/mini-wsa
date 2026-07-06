package com.akamai.wsa.testsupport;

import com.akamai.wsa.domain.model.Action;
import com.akamai.wsa.domain.model.AttackCategory;
import com.akamai.wsa.domain.model.AttackType;
import com.akamai.wsa.domain.model.ClientIp;
import com.akamai.wsa.domain.model.DataLogRecord;
import com.akamai.wsa.domain.model.GeoLocation;
import com.akamai.wsa.domain.model.Rule;
import com.akamai.wsa.domain.model.SecurityEvent;
import com.akamai.wsa.domain.model.Severity;
import com.akamai.wsa.domain.model.ThreatScore;

import java.time.Instant;

/**
 * Test-only builders for fully-populated domain events, so tests stay readable.
 */
public final class SecurityEventFixtures {

    private SecurityEventFixtures() {
    }

    public static DataLogRecord dataLogRecord(String eventId, int configId, String clientIpValue, String path) {
        return new DataLogRecord(
                eventId,
                Instant.parse("2026-05-20T14:32:10Z"),
                configId,
                "pol_web1",
                new ClientIp(clientIpValue),
                "www.example.com",
                path,
                "POST",
                403,
                "Mozilla/5.0",
                new Rule("950001", "SQL_INJECTION", "SQL Injection Attack Detected",
                        Severity.CRITICAL, AttackCategory.INJECTION),
                Action.DENY,
                new GeoLocation("CN", "Beijing"),
                1024,
                256
        );
    }

    public static SecurityEvent enrichedEvent(String eventId, int configId, String clientIpValue, String path) {
        return new SecurityEvent(
                dataLogRecord(eventId, configId, clientIpValue, path),
                AttackType.SQL_COMMAND_INJECTION,
                new ThreatScore(75),
                Instant.parse("2026-05-20T14:32:10.512Z")
        );
    }
}
