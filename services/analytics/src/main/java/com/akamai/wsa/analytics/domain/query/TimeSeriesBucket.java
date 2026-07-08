package com.akamai.wsa.analytics.domain.query;

import java.time.Instant;

public record TimeSeriesBucket(Instant bucketStart, long count) {
}
