package com.akamai.wsa.application.stats;

import com.akamai.wsa.domain.model.TimeRange;

/**
 * Query parameters for the statistics summary. A null configId aggregates
 * across all configurations.
 */
public record StatisticsQuery(Integer configId, TimeRange timeRange) {

    public StatisticsQuery {
        if (timeRange == null) {
            throw new IllegalArgumentException("timeRange must not be null (use TimeRange.unbounded())");
        }
    }
}
