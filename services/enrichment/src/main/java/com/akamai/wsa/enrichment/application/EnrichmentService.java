package com.akamai.wsa.enrichment.application;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import com.akamai.wsa.enrichment.domain.port.ProcessedEventLog;
import com.akamai.wsa.enrichment.domain.service.AttackTypeClassifier;
import com.akamai.wsa.enrichment.domain.service.ThreatScoreCalculator;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class EnrichmentService {

    static final Duration REPEAT_OFFENDER_WINDOW = Duration.ofMinutes(10);

    private final Clock clock;
    private final ProcessedEventLog processedEventLog;
    private final OffenderWindow offenderWindow;
    private final AttackTypeClassifier attackTypeClassifier;
    private final ThreatScoreCalculator threatScoreCalculator;

    public EnrichmentService(Clock clock,
                             ProcessedEventLog processedEventLog,
                             OffenderWindow offenderWindow,
                             AttackTypeClassifier attackTypeClassifier,
                             ThreatScoreCalculator threatScoreCalculator) {
        this.clock = clock;
        this.processedEventLog = processedEventLog;
        this.offenderWindow = offenderWindow;
        this.attackTypeClassifier = attackTypeClassifier;
        this.threatScoreCalculator = threatScoreCalculator;
    }

    public Optional<EnrichedEventMessage> enrich(RawEventMessage rawEvent) {
        if (!processedEventLog.markProcessed(rawEvent.eventId())) {
            return Optional.empty();
        }

        Instant receivedAt = clock.instant();
        offenderWindow.recordEvent(rawEvent.clientIp(), receivedAt);
        long offenderEventCount = offenderWindow.countRecentEventsFromClient(
                rawEvent.clientIp(), REPEAT_OFFENDER_WINDOW, receivedAt);

        Map<String, Object> facts = Map.of(
                "severity", rawEvent.rule().severity().name(),
                "action", rawEvent.action().name(),
                "category", rawEvent.rule().category().name(),
                "path", rawEvent.path(),
                "method", rawEvent.method(),
                "statusCode", rawEvent.statusCode(),
                "clientIp", rawEvent.clientIp(),
                "offenderEventCount", offenderEventCount);
        int threatScore = threatScoreCalculator.calculate(facts).value();
        String attackType = attackTypeClassifier.displayNameFor(rawEvent.rule().category());

        return Optional.of(new EnrichedEventMessage(rawEvent, attackType, threatScore, receivedAt));
    }
}
