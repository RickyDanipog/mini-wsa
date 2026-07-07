package com.akamai.wsa.analytics.interfaces.rest;

import com.akamai.wsa.analytics.application.stats.SummarizeStatistics;
import com.akamai.wsa.analytics.domain.query.AttackerStatistics;
import com.akamai.wsa.analytics.domain.query.CategoryStatistics;
import com.akamai.wsa.analytics.domain.query.PathStatistics;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatsController.class)
class StatsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SummarizeStatistics summarizeStatistics;

    private StatisticsSummary sampleSummary() {
        Map<AttackCategory, CategoryStatistics> byCategory = new LinkedHashMap<>();
        byCategory.put(AttackCategory.INJECTION, new CategoryStatistics(2, 74.5));
        Map<Action, Long> byAction = new LinkedHashMap<>();
        byAction.put(Action.DENY, 2L);
        return new StatisticsSummary(
                14227,
                new TimeRange(Instant.parse("2026-05-20T00:00:00Z"), Instant.parse("2026-05-21T00:00:00Z")),
                2,
                byCategory,
                byAction,
                List.of(new AttackerStatistics("203.0.113.42", 2, 66.666)),
                List.of(new PathStatistics("/api/v1/login", 2)));
    }

    @Test
    void returnsSummaryInExactShapeWithRoundedAverages() throws Exception {
        when(summarizeStatistics.summarize(any())).thenReturn(sampleSummary());

        mockMvc.perform(get("/v1/stats/summary")
                        .param("configId", "14227")
                        .param("from", "2026-05-20T00:00:00Z")
                        .param("to", "2026-05-21T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configId").value(14227))
                .andExpect(jsonPath("$.timeRange.from").value("2026-05-20T00:00:00Z"))
                .andExpect(jsonPath("$.timeRange.to").value("2026-05-21T00:00:00Z"))
                .andExpect(jsonPath("$.totalEvents").value(2))
                .andExpect(jsonPath("$.byCategory.INJECTION.count").value(2))
                .andExpect(jsonPath("$.byCategory.INJECTION.avgThreatScore").value(74.5))
                .andExpect(jsonPath("$.byAction.DENY").value(2))
                .andExpect(jsonPath("$.topAttackers[0].clientIp").value("203.0.113.42"))
                .andExpect(jsonPath("$.topAttackers[0].count").value(2))
                .andExpect(jsonPath("$.topAttackers[0].avgThreatScore").value(66.7))
                .andExpect(jsonPath("$.topTargetedPaths[0].path").value("/api/v1/login"))
                .andExpect(jsonPath("$.topTargetedPaths[0].count").value(2));
    }

    @Test
    void passesNullConfigIdAndUnboundedRangeWhenParamsOmitted() throws Exception {
        when(summarizeStatistics.summarize(any())).thenReturn(sampleSummary());

        mockMvc.perform(get("/v1/stats/summary")).andExpect(status().isOk());

        ArgumentCaptor<StatisticsQuery> captor = ArgumentCaptor.forClass(StatisticsQuery.class);
        verify(summarizeStatistics).summarize(captor.capture());
        assertThat(captor.getValue().configId()).isNull();
        assertThat(captor.getValue().timeRange().from()).isNull();
        assertThat(captor.getValue().timeRange().to()).isNull();
    }

    @Test
    void rejectsMalformedTimestampWith400() throws Exception {
        mockMvc.perform(get("/v1/stats/summary").param("from", "not-a-timestamp"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }
}
