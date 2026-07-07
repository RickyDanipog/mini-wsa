package com.akamai.wsa.eventstore.infrastructure.config;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.MessageEnvelope;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * Consumer wiring for {@code events.enriched}. A bare {@link JsonDeserializer}
 * erases the envelope's type parameter and would deliver
 * {@code MessageEnvelope<LinkedHashMap>}; here the deserializer target is pinned
 * to the parametric {@code MessageEnvelope<EnrichedEventMessage>} and type
 * headers are disabled so the configured type wins over any producer header.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, MessageEnvelope<EnrichedEventMessage>> enrichedConsumerFactory(
            KafkaProperties kafkaProperties) {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JavaType envelopeType = objectMapper.getTypeFactory()
                .constructParametricType(MessageEnvelope.class, EnrichedEventMessage.class);
        JsonDeserializer<MessageEnvelope<EnrichedEventMessage>> valueDeserializer =
                new JsonDeserializer<>(envelopeType, objectMapper);
        valueDeserializer.setUseTypeHeaders(false);
        valueDeserializer.addTrustedPackages("com.akamai.wsa.contracts");
        return new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(null),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(valueDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MessageEnvelope<EnrichedEventMessage>>
            enrichedListenerContainerFactory(
                    ConsumerFactory<String, MessageEnvelope<EnrichedEventMessage>> enrichedConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, MessageEnvelope<EnrichedEventMessage>>();
        factory.setConsumerFactory(enrichedConsumerFactory);
        // Dead-letter: wrap with a DefaultErrorHandler + DeadLetterPublishingRecoverer in hardening (SDD §9).
        return factory;
    }
}
