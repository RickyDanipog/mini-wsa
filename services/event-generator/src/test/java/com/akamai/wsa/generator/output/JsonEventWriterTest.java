package com.akamai.wsa.generator.output;

import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.generator.generate.SecurityEventGenerator;
import com.akamai.wsa.generator.model.GeneratedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class JsonEventWriterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final JsonEventWriter writer = new JsonEventWriter(objectMapper);

    @Test
    void serializesEventsToIngestibleJsonArray() throws Exception {
        GeneratedEvent event = new SecurityEventGenerator(Instant.parse("2026-05-20T14:00:00Z"))
                .generateNormalEvent(0, new Random(1L));

        String json = writer.toJsonArray(List.of(event));
        JsonNode array = objectMapper.readTree(json);
        JsonNode first = array.get(0);

        assertThat(array.isArray()).isTrue();
        assertThat(first.get("eventId").asText()).isEqualTo(event.eventId());
        assertThat(first.get("rule").get("category").asText()).isEqualTo(event.rule().category());
        assertThat(first.get("geoLocation").get("country").asText()).isEqualTo(event.geoLocation().country());
        assertThat(first.get("timestamp").asText()).isEqualTo("2026-05-20T14:00:00Z");
    }

    @Test
    void serializedJsonDeserializesIntoContractRawEventMessage() throws Exception {
        GeneratedEvent event = new SecurityEventGenerator(Instant.parse("2026-05-20T14:00:00Z"))
                .generateNormalEvent(5, new Random(11L));

        String json = writer.toJsonArray(List.of(event));
        RawEventMessage[] parsed = objectMapper.readValue(json, RawEventMessage[].class);

        assertThat(parsed).hasSize(1);
        RawEventMessage raw = parsed[0];
        assertThat(raw.eventId()).isEqualTo(event.eventId());
        assertThat(raw.timestamp()).isEqualTo(event.timestamp());
        assertThat(raw.configId()).isEqualTo(event.configId());
        assertThat(raw.action().name()).isEqualTo(event.action());
        assertThat(raw.rule().category().name()).isEqualTo(event.rule().category());
        assertThat(raw.rule().severity().name()).isEqualTo(event.rule().severity());
        assertThat(raw.geoLocation().country()).isEqualTo(event.geoLocation().country());
    }
}
