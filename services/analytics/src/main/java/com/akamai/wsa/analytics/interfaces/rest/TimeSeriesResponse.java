package com.akamai.wsa.analytics.interfaces.rest;

import com.akamai.wsa.analytics.domain.query.TimeSeriesResult;

import java.time.Instant;
import java.util.List;

public record TimeSeriesResponse(
        Integer configId,
        TimeRangeResponse timeRange,
        String interval,
        List<Bucket> buckets) {

    public record TimeRangeResponse(Instant from, Instant to) {
    }

    public record Bucket(Instant bucketStart, long count) {
    }

    public static TimeSeriesResponse from(TimeSeriesResult result) {
        List<Bucket> buckets = result.buckets().stream()
                .map(bucket -> new Bucket(bucket.bucketStart(), bucket.count()))
                .toList();

        TimeRangeResponse timeRange = new TimeRangeResponse(
                result.timeRange().from(), result.timeRange().to());

        return new TimeSeriesResponse(result.configId(), timeRange, result.interval().label(), buckets);
    }
}
