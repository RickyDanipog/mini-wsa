package com.akamai.wsa.eventstore.interfaces.messaging;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.GeoLocationMessage;
import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.contracts.RuleMessage;
import com.akamai.wsa.contracts.Severity;
import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import com.akamai.wsa.eventstore.domain.port.EventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"events.enriched"})
class EnrichedEventListenerIntegrationTest {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    EventStore eventStore;

    @Test
    void persistsEnrichedEventIdempotentlyByEventId() throws Exception {
        MessageEnvelope<EnrichedEventMessage> envelope = enrichedEnvelope("evt-00132");

        try (Producer<String, String> producer = createProducer()) {
            String json = objectMapper.writeValueAsString(envelope);
            producer.send(new ProducerRecord<>("events.enriched", "14227", json));
            producer.send(new ProducerRecord<>("events.enriched", "14227", json));
            producer.flush();
        }

        await().atMost(Duration.ofSeconds(15))
                .until(() -> eventStore.findByConfigId(14227).stream()
                        .anyMatch(stored -> stored.eventId().equals("evt-00132")));

        assertThat(eventStore.countAll()).isEqualTo(1);
        assertThat(eventStore.findByConfigId(14227)).extracting(StoredEvent::eventId).containsExactly("evt-00132");
    }

    private MessageEnvelope<EnrichedEventMessage> enrichedEnvelope(String eventId) {
        RawEventMessage rawEvent = new RawEventMessage(
                eventId, Instant.parse("2026-05-20T14:32:10Z"), 14227, "pol_web1",
                "203.0.113.42", "www.example.com", "/api/v1/login", "POST", 403, "Mozilla/5.0",
                new RuleMessage("950001", "SQL_INJECTION", "SQL Injection Attack Detected",
                        Severity.CRITICAL, AttackCategory.INJECTION),
                Action.DENY, new GeoLocationMessage("CN", "Beijing"), 1024, 256);
        EnrichedEventMessage enriched = new EnrichedEventMessage(
                rawEvent, "SQL/Command Injection", 75, Instant.parse("2026-05-20T14:32:10.512Z"));
        return MessageEnvelope.of("corr-3", Instant.parse("2026-05-20T14:32:12Z"), enriched);
    }

    private Producer<String, String> createProducer() {
        return new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(embeddedKafka), new StringSerializer(), new StringSerializer())
                .createProducer();
    }
}
