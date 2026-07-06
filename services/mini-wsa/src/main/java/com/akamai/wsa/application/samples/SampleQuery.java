package com.akamai.wsa.application.samples;

import com.akamai.wsa.domain.model.Action;
import com.akamai.wsa.domain.model.AttackCategory;
import com.akamai.wsa.domain.model.TimeRange;

/**
 * Filters and pagination for sample retrieval. All filters are optional; a null
 * configId/category/action means "no filter on that field".
 */
public record SampleQuery(
        Integer configId,
        TimeRange timeRange,
        AttackCategory category,
        Action action,
        int limit,
        int offset
) {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAXIMUM_LIMIT = 100;

    public SampleQuery {
        if (timeRange == null) {
            throw new IllegalArgumentException("timeRange must not be null (use TimeRange.unbounded())");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
    }

    /**
     * Clamps a requested limit into [1, MAXIMUM_LIMIT], applying the default when absent.
     */
    public static int clampLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAXIMUM_LIMIT, requestedLimit));
    }
}
