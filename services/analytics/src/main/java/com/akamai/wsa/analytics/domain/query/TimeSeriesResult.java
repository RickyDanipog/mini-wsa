package com.akamai.wsa.analytics.domain.query;

import java.util.List;

public record TimeSeriesResult(
        Integer configId, TimeRange timeRange, Interval interval, List<TimeSeriesBucket> buckets) {
}
