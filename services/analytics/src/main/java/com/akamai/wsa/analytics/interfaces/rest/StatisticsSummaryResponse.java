package com.akamai.wsa.analytics.interfaces.rest;

import com.akamai.wsa.analytics.domain.query.StatisticsSummary;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record StatisticsSummaryResponse(
        Integer configId,
        TimeRangeResponse timeRange,
        long totalEvents,
        Map<String, CategoryStat> byCategory,
        Map<String, Long> byAction,
        List<AttackerStat> topAttackers,
        List<PathStat> topTargetedPaths) {

    public record TimeRangeResponse(Instant from, Instant to) {
    }

    public record CategoryStat(long count, double avgThreatScore) {
    }

    public record AttackerStat(String clientIp, long count, double avgThreatScore) {
    }

    public record PathStat(String path, long count) {
    }

    public static StatisticsSummaryResponse from(StatisticsSummary summary) {
        Map<String, CategoryStat> byCategory = new LinkedHashMap<>();
        summary.byCategory().forEach((category, stat) ->
                byCategory.put(category.name(), new CategoryStat(stat.count(), round(stat.averageThreatScore()))));

        Map<String, Long> byAction = new LinkedHashMap<>();
        summary.byAction().forEach((action, count) -> byAction.put(action.name(), count));

        List<AttackerStat> topAttackers = summary.topAttackers().stream()
                .map(attacker -> new AttackerStat(attacker.clientIp(), attacker.count(),
                        round(attacker.averageThreatScore())))
                .toList();

        List<PathStat> topTargetedPaths = summary.topTargetedPaths().stream()
                .map(path -> new PathStat(path.path(), path.count()))
                .toList();

        TimeRangeResponse timeRange = new TimeRangeResponse(
                summary.timeRange().from(), summary.timeRange().to());

        return new StatisticsSummaryResponse(summary.configId(), timeRange, summary.totalEvents(),
                byCategory, byAction, topAttackers, topTargetedPaths);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
