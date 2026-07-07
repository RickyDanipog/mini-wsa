package com.akamai.wsa.contracts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static RawEventMessage sampleRaw() {
        return new RawEventMessage(
                "evt-00132",
                Instant.parse("2026-05-20T14:32:10Z"),
                14227,
                "pol_web1",
                "203.0.113.42",
                "www.example.com",
                "/api/v1/login",
                "POST",
                403,
                "Mozilla/5.0",
                new RuleMessage("950001", "SQL_INJECTION", "SQL Injection Attack Detected",
                        Severity.CRITICAL, AttackCategory.INJECTION),
                Action.DENY,
                new GeoLocationMessage("CN", "Beijing"),
                1024,
                256);
    }

    @Test
    void roundTripsARawEventEnvelope() throws Exception {
        MessageEnvelope<RawEventMessage> envelope =
                MessageEnvelope.of("corr-1", Instant.parse("2026-05-20T14:32:11Z"), sampleRaw());

        String json = objectMapper.writeValueAsString(envelope);
        MessageEnvelope<RawEventMessage> back =
                objectMapper.readValue(json, new TypeReference<>() {
                });

        assertThat(back.correlationId()).isEqualTo("corr-1");
        assertThat(back.version()).isEqualTo(MessageEnvelope.CURRENT_VERSION);
        assertThat(back.payload().rule().category()).isEqualTo(AttackCategory.INJECTION);
        assertThat(back.payload().action()).isEqualTo(Action.DENY);
        assertThat(back.payload().timestamp()).isEqualTo(Instant.parse("2026-05-20T14:32:10Z"));
    }

    @Test
    void roundTripsAnEnrichedEventEnvelope() throws Exception {
        EnrichedEventMessage enriched = new EnrichedEventMessage(
                sampleRaw(), "SQL/Command Injection", 75, Instant.parse("2026-05-20T14:32:10.512Z"));
        MessageEnvelope<EnrichedEventMessage> envelope =
                MessageEnvelope.of("corr-2", Instant.parse("2026-05-20T14:32:12Z"), enriched);

        String json = objectMapper.writeValueAsString(envelope);
        MessageEnvelope<EnrichedEventMessage> back =
                objectMapper.readValue(json, new TypeReference<>() {
                });

        assertThat(back.payload().attackType()).isEqualTo("SQL/Command Injection");
        assertThat(back.payload().threatScore()).isEqualTo(75);
        assertThat(back.payload().rawEvent().eventId()).isEqualTo("evt-00132");
        assertThat(back.payload().receivedAt()).isEqualTo(Instant.parse("2026-05-20T14:32:10.512Z"));
    }
}
