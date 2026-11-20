package com.akamai.wsa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = "wsa.rate-limit.requests-per-minute=12")
@AutoConfigureMockMvc
class ApiIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-20T09:04:00Z");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        POSTGRES.start();
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc api;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockBean Clock clock;

    @BeforeEach
    void reset() {
        jdbc.update("TRUNCATE events, alert_rules");
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void rejectsTheWholeBatchWithFieldDetails() throws Exception {
        ObjectNode missingIp = event("bad", NOW, 1, "XSS");
        missingIp.remove("clientIp");
        ObjectNode badTimestamp = event("bad-time", NOW, 1, "XSS").put("timestamp", 42);
        ObjectNode badEnum = event("bad-enum", NOW, 1, "XSS").put("action", "IGNORE");
        ObjectNode fractional = event("fractional", NOW, 1, "XSS").put("configId", 1.25);
        var input = json.createArrayNode().add(event("ok", NOW, 1, "BOT"))
                .add(missingIp).add(badTimestamp).addNull().add(badEnum).add(fractional);
        api.perform(post("/v1/events/ingest").contentType("application/json").content(input.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("[1].clientIp"))
                .andExpect(jsonPath("$.error.details[1].field").value("[2].timestamp"))
                .andExpect(jsonPath("$.error.details[2].field").value("[3]"))
                .andExpect(jsonPath("$.error.details[3].field").value("[4].action"))
                .andExpect(jsonPath("$.error.details[4].field").value("[5].configId"));

        api.perform(post("/v1/events/ingest").contentType("application/json").content("null"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("body"));
        api.perform(post("/v1/events/ingest").contentType("application/json")
                        .content(event("trailing", NOW, 1, "BOT") + " {}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM events", Long.class)).isZero();
    }

    @Test
    void ingestsScoresAndQueriesThroughTheApi() throws Exception {
        var input = json.createArrayNode();
        Instant from = NOW.minusSeconds(360);
        for (int i = 0; i < 6; i++) {
            input.add(event("event-" + i, from.plusSeconds(i * 61L), 1, "INJECTION"));
        }
        input.add(event("another-config", NOW, 2, "BOT"));
        api.perform(post("/v1/events/ingest").contentType("application/json").content(input.toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.acceptedCount").value(7));
        api.perform(post("/v1/events/ingest").contentType("application/json").content(input.get(0).toString()))
                .andExpect(status().isCreated());

        api.perform(get("/v1/stats/summary").param("configId", "1").param("from", from.toString())
                        .param("to", NOW.toString()).with(request -> { request.setRemoteAddr("192.0.2.1"); return request; }))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalEvents").value(6))
                .andExpect(jsonPath("$.byCategory.INJECTION.avgThreatScore").value(77.5))
                .andExpect(jsonPath("$.byAction.DENY").value(6))
                .andExpect(jsonPath("$.topAttackers[0].clientIp").value("203.0.113.7"))
                .andExpect(jsonPath("$.topTargetedPaths[0].path").value("/login"));
        api.perform(get("/v1/events/samples").param("category", "INJECTION").param("action", "DENY")
                        .param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.results[0].eventId").value("event-5"))
                .andExpect(jsonPath("$.results[0].attackType").value("SQL/Command Injection"))
                .andExpect(jsonPath("$.results[0].threatScore").value(90))
                .andExpect(jsonPath("$.results[0].receivedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.results[0].rule.message").value("Test event"));
        api.perform(get("/v1/events/samples").param("limit", "1").param("offset", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.results[0].eventId").value("event-4"));
        api.perform(get("/v1/stats/summary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalEvents").value(7));
        api.perform(get("/v1/stats/timeseries").param("configId", "1").param("interval", "5m"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.buckets[0].count").value(2))
                .andExpect(jsonPath("$.buckets[1].count").value(4));
        api.perform(post("/v1/alerts/define").contentType("application/json")
                        .content("""
                                {"category":"INJECTION","threshold":5,"windowMinutes":10}
                                """))
                .andExpect(status().isCreated());
        api.perform(get("/v1/alerts/evaluate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alerts[0].count").value(6))
                .andExpect(jsonPath("$.alerts[0].firing").value(true));
    }

    @Test
    void serializesTheSixthEventForConcurrentRequests() throws Exception {
        var ready = new CountDownLatch(6);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(6)) {
            var requests = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int index = 0; index < 6; index++) {
                String input = event("concurrent-" + index, NOW, 1, "BOT").toString();
                requests.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Requests did not start");
                    }
                    return api.perform(post("/v1/events/ingest").contentType("application/json").content(input))
                            .andReturn().getResponse().getStatus();
                }));
            }
            boolean allReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertThat(allReady).isTrue();
            for (var request : requests) {
                assertThat(request.get(15, TimeUnit.SECONDS)).isEqualTo(201);
            }
        }
        assertThat(jdbc.queryForList("SELECT threat_score FROM events ORDER BY threat_score", Integer.class))
                .containsExactly(75, 75, 75, 75, 75, 90);

        when(clock.instant()).thenReturn(NOW.plusSeconds(600));
        api.perform(post("/v1/events/ingest").contentType("application/json")
                        .content(event("after-window", NOW, 1, "BOT").toString()))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT threat_score FROM events WHERE event_id = 'after-window'", Integer.class))
                .isEqualTo(75);
    }

    @Test
    void rateLimitsStatsAndSamplesTogetherForOneClient() throws Exception {
        for (int i = 0; i < 12; i++) {
            String path = i % 2 == 0 ? "/v1/stats/summary" : "/v1/events/samples";
            api.perform(get(path).with(request -> { request.setRemoteAddr("192.0.2.2"); return request; }))
                    .andExpect(status().isOk());
        }
        api.perform(get("/v1/events/samples").with(request -> { request.setRemoteAddr("192.0.2.2"); return request; }))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        api.perform(get("/v1/events/samples").with(request -> { request.setRemoteAddr("192.0.2.3"); return request; }))
                .andExpect(status().isOk());
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        api.perform(get("/v1/events/samples").with(request -> { request.setRemoteAddr("192.0.2.2"); return request; }))
                .andExpect(status().isOk());
    }

    private ObjectNode event(String id, Instant timestamp, int config, String category) {
        ObjectNode event = json.createObjectNode();
        event.put("eventId", id).put("timestamp", timestamp.toString()).put("configId", config)
                .put("clientIp", "203.0.113.7").put("path", "/login").put("action", "DENY");
        event.putObject("rule").put("id", "rule-1").put("name", "Test rule")
                .put("message", "Test event").put("severity", "CRITICAL").put("category", category);
        return event;
    }
}
