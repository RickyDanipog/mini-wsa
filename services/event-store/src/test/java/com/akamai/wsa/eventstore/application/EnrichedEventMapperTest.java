package com.akamai.wsa.eventstore.application;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.GeoLocationMessage;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.contracts.RuleMessage;
import com.akamai.wsa.contracts.Severity;
import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EnrichedEventMapperTest {

    @Test
    void mapsEveryFieldFromNestedContract() {
        RawEventMessage rawEvent = new RawEventMessage(
                "evt-00132", Instant.parse("2026-05-20T14:32:10Z"), 14227, "pol_web1",
                "203.0.113.42", "www.example.com", "/api/v1/login", "POST", 403, "Mozilla/5.0",
                new RuleMessage("950001", "SQL_INJECTION", "SQL Injection Attack Detected",
                        Severity.CRITICAL, AttackCategory.INJECTION),
                Action.DENY, new GeoLocationMessage("CN", "Beijing"), 1024, 256);
        EnrichedEventMessage enriched = new EnrichedEventMessage(
                rawEvent, "SQL/Command Injection", 75, Instant.parse("2026-05-20T14:32:10.512Z"));

        StoredEvent stored = EnrichedEventMapper.toStoredEvent(enriched);

        assertThat(stored.eventId()).isEqualTo("evt-00132");
        assertThat(stored.timestamp()).isEqualTo(Instant.parse("2026-05-20T14:32:10Z"));
        assertThat(stored.configId()).isEqualTo(14227);
        assertThat(stored.policyId()).isEqualTo("pol_web1");
        assertThat(stored.clientIp()).isEqualTo("203.0.113.42");
        assertThat(stored.hostname()).isEqualTo("www.example.com");
        assertThat(stored.path()).isEqualTo("/api/v1/login");
        assertThat(stored.method()).isEqualTo("POST");
        assertThat(stored.statusCode()).isEqualTo(403);
        assertThat(stored.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(stored.rule().id()).isEqualTo("950001");
        assertThat(stored.rule().name()).isEqualTo("SQL_INJECTION");
        assertThat(stored.rule().message()).isEqualTo("SQL Injection Attack Detected");
        assertThat(stored.rule().severity()).isEqualTo(Severity.CRITICAL);
        assertThat(stored.rule().category()).isEqualTo(AttackCategory.INJECTION);
        assertThat(stored.action()).isEqualTo(Action.DENY);
        assertThat(stored.geoLocation().country()).isEqualTo("CN");
        assertThat(stored.geoLocation().city()).isEqualTo("Beijing");
        assertThat(stored.requestSize()).isEqualTo(1024);
        assertThat(stored.responseSize()).isEqualTo(256);
        assertThat(stored.attackType()).isEqualTo("SQL/Command Injection");
        assertThat(stored.threatScore()).isEqualTo(75);
        assertThat(stored.receivedAt()).isEqualTo(Instant.parse("2026-05-20T14:32:10.512Z"));
    }
}
