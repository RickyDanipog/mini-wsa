package com.akamai.wsa.application.stats;

import com.akamai.wsa.domain.model.Action;
import com.akamai.wsa.domain.model.AttackCategory;
import com.akamai.wsa.domain.model.TimeRange;

import java.util.List;
import java.util.Map;

/**
 * Aggregated statistics for a configuration and time range, as returned by the
 * summary endpoint. topAttackers and topTargetedPaths are the top 10 by count.
 */
public record StatisticsSummary(
        Integer configId,
        TimeRange timeRange,
        long totalEvents,
        Map<AttackCategory, CategoryStatistics> byCategory,
        Map<Action, Long> byAction,
        List<AttackerStatistics> topAttackers,
        List<PathStatistics> topTargetedPaths
) {
    public static final int TOP_LIMIT = 10;
}
