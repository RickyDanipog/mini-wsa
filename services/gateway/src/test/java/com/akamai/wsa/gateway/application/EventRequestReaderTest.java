package com.akamai.wsa.gateway.application;

import com.akamai.wsa.gateway.interfaces.rest.MalformedRequestException;
import com.akamai.wsa.gateway.interfaces.rest.dto.IngestEventRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventRequestReaderTest {

    private static final String VALID_EVENT = """
            {"eventId":"evt-1","timestamp":"2026-05-20T14:32:10Z","configId":14227,"clientIp":"203.0.113.42",
             "path":"/api/v1/login","statusCode":403,
             "rule":{"id":"950001","name":"SQL_INJECTION","message":"m","severity":"CRITICAL","category":"INJECTION"},
             "action":"DENY","requestSize":1024,"responseSize":256}""";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final EventRequestReader eventRequestReader = new EventRequestReader(objectMapper);

    @Test
    void readsASingleObject() throws Exception {
        JsonNode body = objectMapper.readTree(VALID_EVENT);

        List<IngestEventRequest> requests = eventRequestReader.read(body);

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).eventId()).isEqualTo("evt-1");
    }

    @Test
    void readsAnArray() throws Exception {
        JsonNode body = objectMapper.readTree("[" + VALID_EVENT + "," + VALID_EVENT + "]");

        List<IngestEventRequest> requests = eventRequestReader.read(body);

        assertThat(requests).hasSize(2);
    }

    @Test
    void rejectsMalformedBody() throws Exception {
        JsonNode body = objectMapper.readTree(VALID_EVENT.replace("\"action\":\"DENY\"", "\"action\":\"NOPE\""));

        assertThatThrownBy(() -> eventRequestReader.read(body))
                .isInstanceOf(MalformedRequestException.class);
    }
}
