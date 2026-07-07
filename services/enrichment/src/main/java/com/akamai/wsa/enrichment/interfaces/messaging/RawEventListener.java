package com.akamai.wsa.enrichment.interfaces.messaging;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.enrichment.application.EnrichmentService;
import com.akamai.wsa.enrichment.infrastructure.messaging.EnrichedEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RawEventListener {

    private static final Logger logger = LoggerFactory.getLogger(RawEventListener.class);

    private final ObjectMapper objectMapper;
    private final EnrichmentService enrichmentService;
    private final EnrichedEventPublisher enrichedEventPublisher;

    public RawEventListener(ObjectMapper objectMapper,
                            EnrichmentService enrichmentService,
                            EnrichedEventPublisher enrichedEventPublisher) {
        this.objectMapper = objectMapper;
        this.enrichmentService = enrichmentService;
        this.enrichedEventPublisher = enrichedEventPublisher;
    }

    @KafkaListener(topics = "${wsa.topics.events-raw}", groupId = "enrichment")
    public void onRawEvent(String message) throws Exception {
        MessageEnvelope<RawEventMessage> incoming =
                objectMapper.readValue(message, new TypeReference<>() {
                });
        RawEventMessage rawEvent = incoming.payload();

        enrichmentService.enrich(rawEvent).ifPresentOrElse(
                enriched -> {
                    logger.info("RawEventListener - enriched eventId={} attackType={} threatScore={} correlationId={}",
                            rawEvent.eventId(), enriched.attackType(), enriched.threatScore(), incoming.correlationId());
                    enrichedEventPublisher.publish(
                            MessageEnvelope.of(incoming.correlationId(), incoming.occurredAt(), enriched));
                },
                () -> logger.info("RawEventListener - duplicate eventId={} skipped correlationId={}",
                        rawEvent.eventId(), incoming.correlationId()));
    }
}
