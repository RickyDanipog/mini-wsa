package com.akamai.wsa.analytics.interfaces.rest;

import com.akamai.wsa.analytics.application.samples.FetchEventSamples;
import com.akamai.wsa.analytics.domain.model.EnrichedEventView;
import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.SampleQuery;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static com.akamai.wsa.analytics.testsupport.EnrichedEventViews.view;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SamplesController.class)
class SamplesControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    FetchEventSamples fetchEventSamples;

    private EventSamplesPage samplePage() {
        EnrichedEventView newest = view("evt-3", 14227, "198.51.100.7", "/admin",
                AttackCategory.BOT, Action.ALERT, 40, Instant.parse("2026-05-20T14:10:00Z"));
        EnrichedEventView older = view("evt-1", 14227, "203.0.113.42", "/api/v1/login",
                AttackCategory.INJECTION, Action.DENY, 80, Instant.parse("2026-05-20T14:00:00Z"));
        return new EventSamplesPage(3, 2, 0, List.of(newest, older));
    }

    @Test
    void returnsSamplesInExactShapeNewestFirst() throws Exception {
        when(fetchEventSamples.fetch(any())).thenReturn(samplePage());

        mockMvc.perform(get("/v1/events/samples").param("configId", "14227").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.limit").value(2))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.results[0].eventId").value("evt-3"))
                .andExpect(jsonPath("$.results[0].configId").value(14227))
                .andExpect(jsonPath("$.results[0].path").value("/admin"))
                .andExpect(jsonPath("$.results[0].rule.category").value("BOT"))
                .andExpect(jsonPath("$.results[0].rule.severity").value("HIGH"))
                .andExpect(jsonPath("$.results[0].action").value("ALERT"))
                .andExpect(jsonPath("$.results[0].geoLocation.country").value("US"))
                .andExpect(jsonPath("$.results[0].attackType").value("BOT attack"))
                .andExpect(jsonPath("$.results[0].threatScore").value(40))
                .andExpect(jsonPath("$.results[0].receivedAt").exists())
                .andExpect(jsonPath("$.results[1].eventId").value("evt-1"));
    }

    @Test
    void clampsExcessiveLimitToMaximum() throws Exception {
        when(fetchEventSamples.fetch(any())).thenReturn(samplePage());

        mockMvc.perform(get("/v1/events/samples").param("limit", "500")).andExpect(status().isOk());

        ArgumentCaptor<SampleQuery> captor = ArgumentCaptor.forClass(SampleQuery.class);
        verify(fetchEventSamples).fetch(captor.capture());
        assertThat(captor.getValue().limit()).isEqualTo(SampleQuery.MAXIMUM_LIMIT);
    }

    @Test
    void defaultsLimitTo20WhenOmitted() throws Exception {
        when(fetchEventSamples.fetch(any())).thenReturn(samplePage());

        mockMvc.perform(get("/v1/events/samples")).andExpect(status().isOk());

        ArgumentCaptor<SampleQuery> captor = ArgumentCaptor.forClass(SampleQuery.class);
        verify(fetchEventSamples).fetch(captor.capture());
        assertThat(captor.getValue().limit()).isEqualTo(SampleQuery.DEFAULT_LIMIT);
    }

    @Test
    void rejectsInvalidCategoryEnumWith400() throws Exception {
        mockMvc.perform(get("/v1/events/samples").param("category", "NOT_A_CATEGORY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }
}
