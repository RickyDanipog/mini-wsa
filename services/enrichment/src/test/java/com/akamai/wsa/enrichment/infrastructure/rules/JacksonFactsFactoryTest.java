package com.akamai.wsa.enrichment.infrastructure.rules;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.GeoLocationMessage;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.contracts.RuleMessage;
import com.akamai.wsa.contracts.Severity;
import com.akamai.wsa.enrichment.ruleengine.Facts;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonFactsFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final JacksonFactsFactory factsFactory = new JacksonFactsFactory(objectMapper);

    private final RawEventMessage event = new RawEventMessage(
            "evt-1", Instant.parse("2026-05-20T14:32:10Z"), 14227, "pol_web1",
            "203.0.113.42", "www.example.com", "/api/v1/login", "POST", 403, "Mozilla/5.0",
            new RuleMessage("950001", "SQL_INJECTION", "SQL Injection Attack Detected",
                    Severity.CRITICAL, AttackCategory.INJECTION),
            Action.DENY, new GeoLocationMessage("CN", "Beijing"), 1024, 256);

    @Test
    void resolvesNestedEnumAndScalarPaths() {
        Facts facts = factsFactory.create(event, 6L);

        assertThat(facts.valueOf("rule.severity")).isEqualTo("CRITICAL");
        assertThat(facts.valueOf("rule.category")).isEqualTo("INJECTION");
        assertThat(facts.valueOf("action")).isEqualTo("DENY");
        assertThat(facts.valueOf("path")).isEqualTo("/api/v1/login");
        assertThat(facts.valueOf("geoLocation.country")).isEqualTo("CN");
    }

    @Test
    void resolvesStatusCodeAsNumber() {
        Facts facts = factsFactory.create(event, 6L);

        assertThat(facts.valueOf("statusCode")).isInstanceOf(Number.class);
        assertThat(((Number) facts.valueOf("statusCode")).intValue()).isEqualTo(403);
    }

    @Test
    void exposesDerivedOffenderEventCount() {
        Facts facts = factsFactory.create(event, 6L);

        assertThat(facts.valueOf("offenderEventCount")).isEqualTo(6L);
    }

    @Test
    void returnsNullForUnknownPath() {
        Facts facts = factsFactory.create(event, 6L);

        assertThat(facts.valueOf("rule.nonexistent")).isNull();
        assertThat(facts.valueOf("does.not.exist")).isNull();
    }
}
