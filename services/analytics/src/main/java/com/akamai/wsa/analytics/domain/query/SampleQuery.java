package com.akamai.wsa.analytics.domain.query;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;

public record SampleQuery(
        Integer configId, TimeRange timeRange, AttackCategory category, Action action, int limit, int offset) {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAXIMUM_LIMIT = 100;

    public SampleQuery {
        if (timeRange == null) {
            throw new IllegalArgumentException("timeRange must not be null");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }

    public static int clampLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAXIMUM_LIMIT, requestedLimit));
    }
}
