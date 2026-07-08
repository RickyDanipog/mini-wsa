package com.akamai.wsa.analytics.domain.query;

public record TimeSeriesQuery(Integer configId, TimeRange timeRange, Interval interval) {
    public TimeSeriesQuery {
        if (timeRange == null) {
            throw new IllegalArgumentException("timeRange must not be null");
        }
        if (interval == null) {
            throw new IllegalArgumentException("interval must not be null");
        }
    }
}
