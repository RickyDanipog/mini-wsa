package com.akamai.wsa.enrichment.application;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.enrichment.domain.port.FactsFactory;
import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import com.akamai.wsa.enrichment.domain.port.ProcessedEventLog;
import com.akamai.wsa.enrichment.domain.service.AttackTypeClassifier;
import com.akamai.wsa.enrichment.domain.service.ThreatScoreCalculator;
import com.akamai.wsa.enrichment.ruleengine.Facts;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class EnrichmentService {

    static final Duration REPEAT_OFFENDER_WINDOW = Duration.ofMinutes(10);

    private final ProcessedEventLog processedEventLog;
    private final OffenderWindow offenderWindow;
    private final AttackTypeClassifier attackTypeClassifier;
    private final ThreatScoreCalculator threatScoreCalculator;
    private final FactsFactory factsFactory;

    public EnrichmentService(ProcessedEventLog processedEventLog,
                       OffenderWindow offenderWindow,
                       AttackTypeClassifier attackTypeClassifier,
                       ThreatScoreCalculator threatScoreCalculator,
                       FactsFactory factsFactory) {
        this.processedEventLog = processedEventLog;
        this.offenderWindow = offenderWindow;
        this.attackTypeClassifier = attackTypeClassifier;
        this.threatScoreCalculator = threatScoreCalculator;
        this.factsFactory = factsFactory;
    }

    public Optional<EnrichedEventMessage> enrich(RawEventMessage rawEvent, Instant receivedAt) {
        boolean isFirstSighting = processedEventLog.markProcessed(rawEvent.eventId());
        if (!isFirstSighting) {
            return Optional.empty();
        }

        offenderWindow.recordEvent(rawEvent.clientIp(), rawEvent.eventId(), receivedAt);
        long offenderEventCount = offenderWindow.countRecentEventsFromClient(
                rawEvent.clientIp(), REPEAT_OFFENDER_WINDOW, receivedAt);

        Facts facts = factsFactory.create(rawEvent, offenderEventCount);
        int threatScore = threatScoreCalculator.calculate(facts).value();
        String attackType = attackTypeClassifier.displayNameFor(rawEvent.rule().category());

        return Optional.of(new EnrichedEventMessage(rawEvent, attackType, threatScore, receivedAt));
    }
}
