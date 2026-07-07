package com.akamai.wsa.enrichment.interfaces.messaging;

import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.enrichment.infrastructure.messaging.EnrichedEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Consumes raw events, produces enriched events. THIN skeleton: attackType is
 * the display-name mapping and threatScore is a placeholder (0) — the real
 * rule-based scoring + repeat-offender window arrive with the enrichment plan.
 */
@Component
public class RawEventListener {

    private final ObjectMapper objectMapper;
    private final EnrichedEventPublisher enrichedEventPublisher;
    private final Clock clock;

    public RawEventListener(ObjectMapper objectMapper, EnrichedEventPublisher enrichedEventPublisher, Clock clock) {
        this.objectMapper = objectMapper;
        this.enrichedEventPublisher = enrichedEventPublisher;
        this.clock = clock;
    }

    @KafkaListener(topics = "${wsa.topics.events-raw}", groupId = "enrichment")
    public void onRawEvent(String message) throws Exception {
        MessageEnvelope<RawEventMessage> incoming =
                objectMapper.readValue(message, new TypeReference<>() {
                });
        RawEventMessage rawEvent = incoming.payload();

        String attackType = displayNameFor(rawEvent.rule().category());
        int placeholderThreatScore = 0;
        Instant receivedAt = Instant.now(clock);

        EnrichedEventMessage enriched =
                new EnrichedEventMessage(rawEvent, attackType, placeholderThreatScore, receivedAt);
        enrichedEventPublisher.publish(
                MessageEnvelope.of(incoming.correlationId(), incoming.occurredAt(), enriched));
    }

    private static String displayNameFor(AttackCategory attackCategory) {
        return switch (attackCategory) {
            case INJECTION -> "SQL/Command Injection";
            case XSS -> "Cross-Site Scripting";
            case PROTOCOL_VIOLATION -> "Protocol Anomaly";
            case DATA_LEAKAGE -> "Data Exfiltration";
            case BOT -> "Bot Activity";
            case DOS -> "Denial of Service";
            case RATE_LIMIT -> "Rate Limiting";
        };
    }
}
