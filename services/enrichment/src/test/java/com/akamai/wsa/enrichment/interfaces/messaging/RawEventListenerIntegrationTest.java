package com.akamai.wsa.enrichment.interfaces.messaging;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.GeoLocationMessage;
import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.contracts.RuleMessage;
import com.akamai.wsa.contracts.Severity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"events.raw", "events.enriched"})
class RawEventListenerIntegrationTest {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    @Test
    void consumesRawEventAndPublishesEnrichedEvent() throws Exception {
        RawEventMessage rawEvent = new RawEventMessage(
                "evt-00132", Instant.parse("2026-05-20T14:32:10Z"), 14227, "pol_web1",
                "203.0.113.42", "www.example.com", "/api/v1/login", "POST", 403, "Mozilla/5.0",
                new RuleMessage("950001", "SQL_INJECTION", "SQL Injection Attack Detected",
                        Severity.CRITICAL, AttackCategory.INJECTION),
                Action.DENY, new GeoLocationMessage("CN", "Beijing"), 1024, 256);
        MessageEnvelope<RawEventMessage> rawEnvelope =
                MessageEnvelope.of("corr-2", Instant.parse("2026-05-20T14:32:11Z"), rawEvent);

        try (Producer<String, String> producer = createProducer();
             Consumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(List.of("events.enriched"));
            producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                    "events.raw", rawEvent.clientIp(), objectMapper.writeValueAsString(rawEnvelope)));
            producer.flush();

            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15));
            assertThat(records.count()).isEqualTo(1);

            ConsumerRecord<String, String> record = records.iterator().next();
            assertThat(record.key()).isEqualTo("14227");
            MessageEnvelope<EnrichedEventMessage> enrichedEnvelope =
                    objectMapper.readValue(record.value(), new TypeReference<>() {
                    });
            assertThat(enrichedEnvelope.correlationId()).isEqualTo("corr-2");
            assertThat(enrichedEnvelope.payload().attackType()).isEqualTo("SQL/Command Injection");
            assertThat(enrichedEnvelope.payload().rawEvent().eventId()).isEqualTo("evt-00132");
            assertThat(enrichedEnvelope.payload().receivedAt()).isNotNull();
        }
    }

    private Producer<String, String> createProducer() {
        return new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(embeddedKafka), new StringSerializer(), new StringSerializer())
                .createProducer();
    }

    private Consumer<String, String> createConsumer() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("enrichment-it", "true", embeddedKafka);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
    }
}
