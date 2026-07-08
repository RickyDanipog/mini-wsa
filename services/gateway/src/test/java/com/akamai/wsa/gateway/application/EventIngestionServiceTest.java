package com.akamai.wsa.gateway.application;

import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.gateway.infrastructure.messaging.RawEventPublisher;
import com.akamai.wsa.gateway.interfaces.rest.dto.IngestEventRequest;
import com.akamai.wsa.gateway.interfaces.rest.error.BatchValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class EventIngestionServiceTest {

    private static final String VALID_EVENT = """
            {"eventId":"evt-1","timestamp":"2026-05-20T14:32:10Z","configId":14227,"clientIp":"203.0.113.42",
             "path":"/api/v1/login","statusCode":403,
             "rule":{"id":"950001","name":"SQL_INJECTION","message":"m","severity":"CRITICAL","category":"INJECTION"},
             "action":"DENY","requestSize":1024,"responseSize":256}""";
    private static final String INVALID_EVENT = VALID_EVENT.replace("\"clientIp\":\"203.0.113.42\",", "");

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = validatorFactory.getValidator();
    private final EventRequestMapper eventRequestMapper = new EventRequestMapper();
    private final RawEventPublisher rawEventPublisher = mock(RawEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-08T00:00:00Z"), ZoneOffset.UTC);

    private final EventIngestionService ingestEvents =
            new EventIngestionService(validator, eventRequestMapper, rawEventPublisher, clock);

    @Test
    void mapsAndPublishesEachValidEventAndReturnsCount() throws Exception {
        List<IngestEventRequest> requests = readAll("[" + VALID_EVENT + "," + VALID_EVENT + "]");

        int accepted = ingestEvents.ingest(requests, "corr-1");

        assertThat(accepted).isEqualTo(2);
        ArgumentCaptor<MessageEnvelope<RawEventMessage>> captor = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(rawEventPublisher, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(envelope -> {
            assertThat(envelope.correlationId()).isEqualTo("corr-1");
            assertThat(envelope.occurredAt()).isEqualTo(Instant.parse("2026-07-08T00:00:00Z"));
            assertThat(envelope.payload().eventId()).isEqualTo("evt-1");
        });
    }

    @Test
    void rejectsWholeBatchWhenAnyEventInvalidAndPublishesNothing() throws Exception {
        List<IngestEventRequest> requests = readAll("[" + VALID_EVENT + "," + INVALID_EVENT + "]");

        assertThatThrownBy(() -> ingestEvents.ingest(requests, "corr-1"))
                .isInstanceOf(BatchValidationException.class);
        verify(rawEventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    private List<IngestEventRequest> readAll(String json) throws Exception {
        return objectMapper.readValue(json, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, IngestEventRequest.class));
    }
}
