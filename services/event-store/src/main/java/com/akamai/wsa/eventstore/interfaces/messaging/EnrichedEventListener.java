package com.akamai.wsa.eventstore.interfaces.messaging;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.eventstore.infrastructure.persistence.inmemory.InMemoryEnrichedEventStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes enriched events and persists them (idempotent by eventId). THIN
 * skeleton over the in-memory store; the Mongo adapter arrives with the
 * event-store plan.
 */
@Component
public class EnrichedEventListener {

    private final ObjectMapper objectMapper;
    private final InMemoryEnrichedEventStore eventStore;

    public EnrichedEventListener(ObjectMapper objectMapper, InMemoryEnrichedEventStore eventStore) {
        this.objectMapper = objectMapper;
        this.eventStore = eventStore;
    }

    @KafkaListener(topics = "${wsa.topics.events-enriched}", groupId = "event-store")
    public void onEnrichedEvent(String message) throws Exception {
        MessageEnvelope<EnrichedEventMessage> incoming =
                objectMapper.readValue(message, new TypeReference<>() {
                });
        eventStore.save(incoming.payload());
    }
}
