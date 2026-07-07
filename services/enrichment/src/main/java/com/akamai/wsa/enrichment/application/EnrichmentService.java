package com.akamai.wsa.enrichment.application;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import com.akamai.wsa.enrichment.domain.port.ProcessedEventLog;
import com.akamai.wsa.enrichment.domain.service.AttackTypeClassifier;
import com.akamai.wsa.enrichment.domain.service.ThreatScoreCalculator;
import com.akamai.wsa.enrichment.domain.service.ThreatScoringInputs;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Orchestrates enrichment of a single raw event: dedup by eventId, record-then-read
 * the offender window, classify the attack type, and compute the threat score.
 * Thin — the graded logic lives in the pure domain services it delegates to.
 */
@Service
public class EnrichmentService {

    static final Duration REPEAT_OFFENDER_WINDOW = Duration.ofMinutes(10);
    static final int REPEAT_OFFENDER_THRESHOLD = 5;

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

    /** Empty when the event was already processed (idempotent on redelivery). */
    public Optional<EnrichedEventMessage> enrich(RawEventMessage rawEvent) {
        if (!processedEventLog.markProcessed(rawEvent.eventId())) {
            return Optional.empty();
        }

        Instant receivedAt = clock.instant();
        offenderWindow.recordEvent(rawEvent.clientIp(), receivedAt);
        long recentCount = offenderWindow.countRecentEventsFromClient(
                rawEvent.clientIp(), REPEAT_OFFENDER_WINDOW, receivedAt);
        boolean repeatOffender = recentCount > REPEAT_OFFENDER_THRESHOLD;

        ThreatScoringInputs threatScoringInputs = new ThreatScoringInputs(
                rawEvent.rule().severity(), rawEvent.action(), rawEvent.path(), repeatOffender);
        int threatScore = threatScoreCalculator.calculate(threatScoringInputs).value();
        String attackType = attackTypeClassifier.displayNameFor(rawEvent.rule().category());

        return Optional.of(new EnrichedEventMessage(rawEvent, attackType, threatScore, receivedAt));
    }
}
