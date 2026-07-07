package com.akamai.wsa.eventstore.interfaces.messaging;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.eventstore.application.EnrichedEventMapper;
import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import com.akamai.wsa.eventstore.domain.port.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consumes enriched events off {@code events.enriched}, maps the wire contract to
 * the owned {@link StoredEvent}, and persists via the {@link EventStore} port
 * (idempotent by eventId — Kafka is at-least-once).
 */
@Component
public class EnrichedEventListener {

    private static final Logger logger = LoggerFactory.getLogger(EnrichedEventListener.class);

    private final EventStore eventStore;

    public EnrichedEventListener(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @KafkaListener(
            topics = "${wsa.topics.enriched}",
            groupId = "${wsa.consumer-group}",
            containerFactory = "enrichedListenerContainerFactory")
    public void onEnrichedEvent(MessageEnvelope<EnrichedEventMessage> enrichedEventEnvelope) {
        StoredEvent storedEvent = EnrichedEventMapper.toStoredEvent(enrichedEventEnvelope.payload());
        eventStore.saveAll(List.of(storedEvent));
        logger.info("EnrichedEventListener - onEnrichedEvent eventId={} correlationId={}",
                storedEvent.eventId(), enrichedEventEnvelope.correlationId());
    }
}
