package com.akamai.wsa.enrichment.application;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.GeoLocationMessage;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.contracts.RuleMessage;
import com.akamai.wsa.contracts.Severity;
import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import com.akamai.wsa.enrichment.domain.port.ProcessedEventLog;
import com.akamai.wsa.enrichment.domain.service.ActionRule;
import com.akamai.wsa.enrichment.domain.service.DefaultAttackTypeClassifier;
import com.akamai.wsa.enrichment.domain.service.RepeatOffenderRule;
import com.akamai.wsa.enrichment.domain.service.RuleBasedThreatScoreCalculator;
import com.akamai.wsa.enrichment.domain.service.SensitivePathRule;
import com.akamai.wsa.enrichment.domain.service.SeverityRule;
import com.akamai.wsa.enrichment.domain.service.ThreatScoreCalculator;
import com.akamai.wsa.enrichment.infrastructure.dedup.InMemoryProcessedEventLog;
import com.akamai.wsa.enrichment.infrastructure.window.InMemoryOffenderWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EnrichmentServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-20T14:32:11Z");

    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private final ThreatScoreCalculator calculator = new RuleBasedThreatScoreCalculator(List.of(
            new SeverityRule(), new ActionRule(), new SensitivePathRule(), new RepeatOffenderRule()));

    private OffenderWindow offenderWindow;
    private ProcessedEventLog processedEventLog;
    private EnrichmentService service;

    @BeforeEach
    void setUp() {
        offenderWindow = new InMemoryOffenderWindow();
        processedEventLog = new InMemoryProcessedEventLog();
        service = new EnrichmentService(
                fixedClock, processedEventLog, offenderWindow, new DefaultAttackTypeClassifier(), calculator);
    }

    private RawEventMessage rawEvent(String eventId) {
        return new RawEventMessage(
                eventId, Instant.parse("2026-05-20T14:32:10Z"), 14227, "pol_web1",
                "203.0.113.42", "www.example.com", "/api/v1/login", "POST", 403, "Mozilla/5.0",
                new RuleMessage("950001", "SQL_INJECTION", "SQL Injection Attack Detected",
                        Severity.CRITICAL, AttackCategory.INJECTION),
                Action.DENY, new GeoLocationMessage("CN", "Beijing"), 1024, 256);
    }

    @Test
    void enrichesSingleEventWithClassificationScoreAndReceivedAt() {
        Optional<EnrichedEventMessage> enriched = service.enrich(rawEvent("evt-1"));

        assertThat(enriched).isPresent();
        assertThat(enriched.get().attackType()).isEqualTo("SQL/Command Injection");
        assertThat(enriched.get().threatScore()).isEqualTo(75);
        assertThat(enriched.get().receivedAt()).isEqualTo(FIXED_NOW);
        assertThat(enriched.get().rawEvent().eventId()).isEqualTo("evt-1");
    }

    @Test
    void sixthEventFromSameClientBecomesRepeatOffender() {
        for (int i = 1; i <= 5; i++) {
            assertThat(service.enrich(rawEvent("evt-" + i)).get().threatScore()).isEqualTo(75);
        }
        assertThat(service.enrich(rawEvent("evt-6")).get().threatScore()).isEqualTo(90);
    }

    @Test
    void duplicateEventIdIsSkippedAndDoesNotInflateOffenderWindow() {
        assertThat(service.enrich(rawEvent("evt-dup"))).isPresent();

        Optional<EnrichedEventMessage> redelivery = service.enrich(rawEvent("evt-dup"));

        assertThat(redelivery).isEmpty();
        assertThat(offenderWindow.countRecentEventsFromClient(
                "203.0.113.42", Duration.ofMinutes(10), FIXED_NOW)).isEqualTo(1);
    }
}
