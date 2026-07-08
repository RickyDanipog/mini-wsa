package com.akamai.wsa.analytics.interfaces.rest;

import com.akamai.wsa.analytics.application.stats.BuildTimeSeries;
import com.akamai.wsa.analytics.domain.query.Interval;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.analytics.domain.query.TimeSeriesBucket;
import com.akamai.wsa.analytics.domain.query.TimeSeriesQuery;
import com.akamai.wsa.analytics.domain.query.TimeSeriesResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TimeSeriesController.class)
class TimeSeriesControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    BuildTimeSeries buildTimeSeries;

    private TimeSeriesResult sampleResult() {
        return new TimeSeriesResult(
                14227,
                new TimeRange(Instant.parse("2026-05-20T14:00:00Z"), Instant.parse("2026-05-20T15:00:00Z")),
                Interval.FIVE_MINUTES,
                List.of(
                        new TimeSeriesBucket(Instant.parse("2026-05-20T14:00:00Z"), 2L),
                        new TimeSeriesBucket(Instant.parse("2026-05-20T14:05:00Z"), 1L)));
    }

    @Test
    void returnsBucketsInExactShape() throws Exception {
        when(buildTimeSeries.build(any())).thenReturn(sampleResult());

        mockMvc.perform(get("/v1/stats/timeseries")
                        .param("configId", "14227")
                        .param("from", "2026-05-20T14:00:00Z")
                        .param("to", "2026-05-20T15:00:00Z")
                        .param("interval", "5m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configId").value(14227))
                .andExpect(jsonPath("$.timeRange.from").value("2026-05-20T14:00:00Z"))
                .andExpect(jsonPath("$.timeRange.to").value("2026-05-20T15:00:00Z"))
                .andExpect(jsonPath("$.interval").value("5m"))
                .andExpect(jsonPath("$.buckets[0].bucketStart").value("2026-05-20T14:00:00Z"))
                .andExpect(jsonPath("$.buckets[0].count").value(2))
                .andExpect(jsonPath("$.buckets[1].bucketStart").value("2026-05-20T14:05:00Z"))
                .andExpect(jsonPath("$.buckets[1].count").value(1));

        ArgumentCaptor<TimeSeriesQuery> captor = ArgumentCaptor.forClass(TimeSeriesQuery.class);
        verify(buildTimeSeries).build(captor.capture());
        assertThat(captor.getValue().interval()).isEqualTo(Interval.FIVE_MINUTES);
    }

    @Test
    void defaultsToOneHourIntervalWhenOmitted() throws Exception {
        when(buildTimeSeries.build(any())).thenReturn(sampleResult());

        mockMvc.perform(get("/v1/stats/timeseries")).andExpect(status().isOk());

        ArgumentCaptor<TimeSeriesQuery> captor = ArgumentCaptor.forClass(TimeSeriesQuery.class);
        verify(buildTimeSeries).build(captor.capture());
        assertThat(captor.getValue().configId()).isNull();
        assertThat(captor.getValue().interval()).isEqualTo(Interval.ONE_HOUR);
    }

    @Test
    void rejectsInvalidIntervalWith400() throws Exception {
        mockMvc.perform(get("/v1/stats/timeseries").param("interval", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details[0]").value("invalid interval: nope (allowed: 1m, 5m, 1h)"));
    }
}
