package com.akamai.wsa.analytics.domain.query;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;

import java.util.List;
import java.util.Map;

public record StatisticsSummary(
        Integer configId, TimeRange timeRange, long totalEvents,
        Map<AttackCategory, CategoryStatistics> byCategory,
        Map<Action, Long> byAction,
        List<AttackerStatistics> topAttackers,
        List<PathStatistics> topTargetedPaths) {
    public static final int TOP_LIMIT = 10;
}
