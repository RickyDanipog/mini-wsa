package com.akamai.wsa.generator.feed;

import com.akamai.wsa.generator.GeneratorProperties;
import com.akamai.wsa.generator.model.GeneratedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class RestClientIngestionClientTest {

    private static final String BASE_URL = "http://localhost:8081";
    private static final String INGEST_URL = "http://localhost:8081/v1/events/ingest";

    private GeneratorProperties props() {
        return new GeneratorProperties(1L, 1, 0, 0, Instant.parse("2026-05-20T14:00:00Z"),
                BASE_URL, 500, GeneratorProperties.OutputMode.HTTP, "out.json");
    }

    private GeneratedEvent anEvent() {
        return new GeneratedEvent("evt-1", Instant.parse("2026-05-20T14:00:00Z"), 14227, "pol",
                "1.1.1.1", "h", "/p", "GET", 200, "ua", null, "DENY", null, 1, 1);
    }

    @Test
    void parsesAcceptedCountFrom201() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(INGEST_URL))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"acceptedCount\":3}"));

        int accepted = new RestClientIngestionClient(builder, props()).postBatch(List.of(anEvent()));

        assertThat(accepted).isEqualTo(3);
        server.verify();
    }

    @Test
    void returnsZeroAndDoesNotThrowOn400() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(INGEST_URL))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"VALIDATION\",\"message\":\"bad batch\"}}"));

        int accepted = new RestClientIngestionClient(builder, props()).postBatch(List.of(anEvent()));

        assertThat(accepted).isZero();
        server.verify();
    }
}
