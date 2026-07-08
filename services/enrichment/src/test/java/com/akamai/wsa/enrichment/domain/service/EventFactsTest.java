package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.GeoLocationMessage;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.contracts.RuleMessage;
import com.akamai.wsa.contracts.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventFactsTest {

    private final RawEventMessage event = new RawEventMessage(
            "evt-1", Instant.parse("2026-05-20T14:32:10Z"), 14227, "pol_web1",
            "203.0.113.42", "www.example.com", "/api/v1/login", "POST", 403, "Mozilla/5.0",
            new RuleMessage("950001", "SQL_INJECTION", "SQL Injection Attack Detected",
                    Severity.CRITICAL, AttackCategory.INJECTION),
            Action.DENY, new GeoLocationMessage("CN", "Beijing"), 1024, 256);

    private final EventFacts facts = new EventFacts(event, 6L);

    @Test
    void exposesWrappedEventFieldsUnderFactKeys() {
        assertThat(facts.valueOf(FactKey.SEVERITY)).isEqualTo("CRITICAL");
        assertThat(facts.valueOf(FactKey.ACTION)).isEqualTo("DENY");
        assertThat(facts.valueOf(FactKey.CATEGORY)).isEqualTo("INJECTION");
        assertThat(facts.valueOf(FactKey.PATH)).isEqualTo("/api/v1/login");
        assertThat(facts.valueOf(FactKey.METHOD)).isEqualTo("POST");
        assertThat(facts.valueOf(FactKey.STATUS_CODE)).isEqualTo(403);
        assertThat(facts.valueOf(FactKey.CLIENT_IP)).isEqualTo("203.0.113.42");
    }

    @Test
    void exposesDerivedOffenderEventCount() {
        assertThat(facts.valueOf(FactKey.OFFENDER_EVENT_COUNT)).isEqualTo(6L);
    }

    @Test
    void returnsNullForUnknownKey() {
        assertThat(facts.valueOf("unknown")).isNull();
    }
}
