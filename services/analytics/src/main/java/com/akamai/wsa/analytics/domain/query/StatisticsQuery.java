package com.akamai.wsa.analytics.domain.query;

public record StatisticsQuery(Integer configId, TimeRange timeRange) {
    public StatisticsQuery {
        if (timeRange == null) {
            throw new IllegalArgumentException("timeRange must not be null");
        }
    }
}
