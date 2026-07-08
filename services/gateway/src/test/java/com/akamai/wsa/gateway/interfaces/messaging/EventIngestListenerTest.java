package com.akamai.wsa.gateway.interfaces.messaging;

import com.akamai.wsa.gateway.application.EventRequestMapper;
import com.akamai.wsa.gateway.application.EventRequestReader;
import com.akamai.wsa.gateway.application.IngestEventsService;
import com.akamai.wsa.gateway.infrastructure.messaging.RawEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EventIngestListenerTest {

    private static final String VALID_EVENT = """
            {"eventId":"evt-1","timestamp":"2026-05-20T14:32:10Z","configId":14227,"clientIp":"203.0.113.42",
             "path":"/api/v1/login","statusCode":403,
             "rule":{"id":"950001","name":"SQL_INJECTION","message":"m","severity":"CRITICAL","category":"INJECTION"},
             "action":"DENY","requestSize":1024,"responseSize":256}""";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final RawEventPublisher rawEventPublisher = mock(RawEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-08T00:00:00Z"), ZoneOffset.UTC);

    private final EventIngestListener listener = new EventIngestListener(
            objectMapper,
            new EventRequestReader(objectMapper),
            new IngestEventsService(validator, new EventRequestMapper(), rawEventPublisher, clock));

    @Test
    void publishesRawEventForValidMessage() {
        listener.onEventIngestMessage(record(VALID_EVENT));

        verify(rawEventPublisher).publish(any());
    }

    @Test
    void logsAndSkipsInvalidJsonWithoutPublishing() {
        assertThatCode(() -> listener.onEventIngestMessage(record("not-json")))
                .doesNotThrowAnyException();

        verify(rawEventPublisher, never()).publish(any());
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("events.ingest", 0, 0L, null, value);
    }
}
