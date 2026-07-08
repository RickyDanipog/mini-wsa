package com.akamai.wsa.analytics.interfaces.rest;

import com.akamai.wsa.analytics.application.alert.AlertService;
import com.akamai.wsa.analytics.domain.alert.AlertEvaluation;
import com.akamai.wsa.analytics.domain.alert.AlertRule;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.contracts.AttackCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertsController.class)
class AlertsControllerTest {

    private static final Instant NOW = Instant.parse("2026-05-20T14:10:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AlertService alertService;

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Test
    void defineReturns201WithGeneratedId() throws Exception {
        when(alertService.defineRule(AttackCategory.INJECTION, 3, 10))
                .thenReturn(new AlertRule("generated-id", AttackCategory.INJECTION, 3, 10));

        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"INJECTION\",\"threshold\":3,\"windowMinutes\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("generated-id"))
                .andExpect(jsonPath("$.category").value("INJECTION"))
                .andExpect(jsonPath("$.threshold").value(3))
                .andExpect(jsonPath("$.windowMinutes").value(10));

        verify(alertService).defineRule(AttackCategory.INJECTION, 3, 10);
    }

    @Test
    void evaluateReflectsFiringFlagsAndDefaultsAsOfToClock() throws Exception {
        TimeRange window = new TimeRange(Instant.parse("2026-05-20T14:00:00Z"), NOW);
        when(alertService.evaluate(eq(NOW))).thenReturn(List.of(
                new AlertEvaluation("rule-injection", AttackCategory.INJECTION, 3, 10, 5, true, window),
                new AlertEvaluation("rule-bot", AttackCategory.BOT, 4, 10, 1, false, window)));

        mockMvc.perform(get("/v1/alerts/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value("2026-05-20T14:10:00Z"))
                .andExpect(jsonPath("$.alerts[0].ruleId").value("rule-injection"))
                .andExpect(jsonPath("$.alerts[0].category").value("INJECTION"))
                .andExpect(jsonPath("$.alerts[0].count").value(5))
                .andExpect(jsonPath("$.alerts[0].firing").value(true))
                .andExpect(jsonPath("$.alerts[0].window.from").value("2026-05-20T14:00:00Z"))
                .andExpect(jsonPath("$.alerts[0].window.to").value("2026-05-20T14:10:00Z"))
                .andExpect(jsonPath("$.alerts[1].ruleId").value("rule-bot"))
                .andExpect(jsonPath("$.alerts[1].firing").value(false));

        verify(alertService).evaluate(NOW);
    }

    @Test
    void evaluateHonoursExplicitAsOf() throws Exception {
        Instant asOf = Instant.parse("2026-05-20T12:00:00Z");
        when(alertService.evaluate(eq(asOf))).thenReturn(List.of());

        mockMvc.perform(get("/v1/alerts/evaluate").param("asOf", "2026-05-20T12:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value("2026-05-20T12:00:00Z"))
                .andExpect(jsonPath("$.alerts").isArray());

        verify(alertService).evaluate(asOf);
    }

    @Test
    void defineRejectsInvalidCategoryWith400() throws Exception {
        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"NOPE\",\"threshold\":3,\"windowMinutes\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("invalid category: NOPE")));
    }

    @Test
    void defineRejectsThresholdBelowOneWith400() throws Exception {
        when(alertService.defineRule(any(AttackCategory.class), anyInt(), anyInt()))
                .thenThrow(new IllegalArgumentException("alert rule threshold must be at least 1"));

        mockMvc.perform(post("/v1/alerts/define")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"INJECTION\",\"threshold\":0,\"windowMinutes\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details[0]").value("alert rule threshold must be at least 1"));
    }
}
