package com.akamai.wsa.gateway.interfaces.rest;

import com.akamai.wsa.gateway.infrastructure.config.GatewayConfiguration;
import com.akamai.wsa.gateway.infrastructure.messaging.RawEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestController.class)
@Import(GatewayConfiguration.class)
class IngestControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RawEventPublisher rawEventPublisher;

    private static final String VALID_EVENT = """
            {"eventId":"evt-1","timestamp":"2026-05-20T14:32:10Z","configId":14227,"clientIp":"203.0.113.42",
             "path":"/api/v1/login","statusCode":403,
             "rule":{"id":"950001","name":"SQL_INJECTION","message":"m","severity":"CRITICAL","category":"INJECTION"},
             "action":"DENY","requestSize":1024,"responseSize":256}""";

    @Test
    void acceptsASingleValidEvent() throws Exception {
        mockMvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedCount").value(1));
        verify(rawEventPublisher).publish(any());
    }

    @Test
    void acceptsAValidBatch() throws Exception {
        mockMvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON)
                        .content("[" + VALID_EVENT + "," + VALID_EVENT + "]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedCount").value(2));
        verify(rawEventPublisher, times(2)).publish(any());
    }

    @Test
    void rejectsBatchWhenAnyEventInvalid_allOrNothing() throws Exception {
        String missingClientIp = VALID_EVENT.replace("\"clientIp\":\"203.0.113.42\",", "");
        mockMvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON)
                        .content("[" + VALID_EVENT + "," + missingClientIp + "]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details").isArray());
        verify(rawEventPublisher, never()).publish(any());
    }

    @Test
    void rejectsInvalidEnum() throws Exception {
        String badAction = VALID_EVENT.replace("\"action\":\"DENY\"", "\"action\":\"NOPE\"");
        mockMvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(badAction))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
        verify(rawEventPublisher, never()).publish(any());
    }

    @Test
    void rejectsMissingRequiredFieldOnSingleEvent() throws Exception {
        String missingEventId = VALID_EVENT.replace("\"eventId\":\"evt-1\",", "");
        mockMvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(missingEventId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        verify(rawEventPublisher, never()).publish(any());
    }
}
