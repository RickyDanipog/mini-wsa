package com.akamai.wsa.generator.output;

import com.akamai.wsa.generator.model.GeneratedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Serializes generated events to the gateway's ingestible JSON array shape
 * (ISO-8601 timestamps, nested rule/geoLocation). The injected Spring-Boot
 * {@code ObjectMapper} is JavaTime-configured, so instants emit as ISO-8601.
 */
@Component
public class JsonEventWriter {

    private final ObjectMapper objectMapper;

    public JsonEventWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJsonArray(List<GeneratedEvent> events) {
        try {
            return objectMapper.writeValueAsString(events);
        } catch (JsonProcessingException serializationFailure) {
            throw new IllegalStateException("failed to serialize generated events", serializationFailure);
        }
    }
}
