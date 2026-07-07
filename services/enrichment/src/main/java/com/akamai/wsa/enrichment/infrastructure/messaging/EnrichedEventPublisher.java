package com.akamai.wsa.enrichment.infrastructure.messaging;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.MessageEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EnrichedEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public EnrichedEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${wsa.topics.events-enriched}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(MessageEnvelope<EnrichedEventMessage> envelope) {
        String key = Integer.toString(envelope.payload().rawEvent().configId());
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, key, json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize enriched event envelope", exception);
        }
    }
}
