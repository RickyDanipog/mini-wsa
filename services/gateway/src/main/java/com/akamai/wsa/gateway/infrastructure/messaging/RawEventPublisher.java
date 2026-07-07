package com.akamai.wsa.gateway.infrastructure.messaging;

import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.contracts.RawEventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes validated raw events to the {@code events.raw} topic as JSON,
 * keyed by clientIp so one attacker's events keep per-partition order.
 */
@Component
public class RawEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public RawEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${wsa.topics.events-raw}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(MessageEnvelope<RawEventMessage> envelope) {
        String key = envelope.payload().clientIp();
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, key, json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize raw event envelope", exception);
        }
    }
}
