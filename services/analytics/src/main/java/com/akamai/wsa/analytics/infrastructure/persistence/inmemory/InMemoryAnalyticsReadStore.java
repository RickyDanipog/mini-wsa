package com.akamai.wsa.analytics.infrastructure.persistence.inmemory;

import com.akamai.wsa.analytics.domain.model.EnrichedEventView;
import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.domain.query.AttackerStatistics;
import com.akamai.wsa.analytics.domain.query.CategoryStatistics;
import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.PathStatistics;
import com.akamai.wsa.analytics.domain.query.SampleQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InMemoryAnalyticsReadStore implements AnalyticsReadStore {

    private final List<EnrichedEventView> events;

    public InMemoryAnalyticsReadStore(List<EnrichedEventView> events) {
        this.events = List.copyOf(events);
    }

    @Override
    public StatisticsSummary summarize(StatisticsQuery statisticsQuery) {
        List<EnrichedEventView> matched = filter(statisticsQuery.configId(), statisticsQuery.timeRange(), null, null);

        Map<AttackCategory, CategoryStatistics> byCategory = matched.stream()
                .collect(Collectors.groupingBy(EnrichedEventView::category))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new CategoryStatistics(
                        entry.getValue().size(),
                        entry.getValue().stream().mapToInt(EnrichedEventView::threatScore).average().orElse(0.0))));

        Map<Action, Long> byAction = matched.stream()
                .collect(Collectors.groupingBy(EnrichedEventView::action, Collectors.counting()));

        List<AttackerStatistics> topAttackers = matched.stream()
                .collect(Collectors.groupingBy(EnrichedEventView::clientIp))
                .entrySet().stream()
                .map(entry -> new AttackerStatistics(entry.getKey(), entry.getValue().size(),
                        entry.getValue().stream().mapToInt(EnrichedEventView::threatScore).average().orElse(0.0)))
                .sorted(Comparator.comparingLong(AttackerStatistics::count).reversed()
                        .thenComparing(AttackerStatistics::clientIp))
                .limit(StatisticsSummary.TOP_LIMIT).toList();

        List<PathStatistics> topTargetedPaths = matched.stream()
                .collect(Collectors.groupingBy(EnrichedEventView::path, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new PathStatistics(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(PathStatistics::count).reversed()
                        .thenComparing(PathStatistics::path))
                .limit(StatisticsSummary.TOP_LIMIT).toList();

        return new StatisticsSummary(statisticsQuery.configId(), statisticsQuery.timeRange(),
                matched.size(), byCategory, byAction, topAttackers, topTargetedPaths);
    }

    @Override
    public EventSamplesPage findSamples(SampleQuery sampleQuery) {
        List<EnrichedEventView> matched = filter(
                sampleQuery.configId(), sampleQuery.timeRange(), sampleQuery.category(), sampleQuery.action());
        List<EnrichedEventView> ordered = matched.stream()
                .sorted(Comparator.comparing(EnrichedEventView::timestamp).reversed())
                .toList();
        List<EnrichedEventView> page = ordered.stream()
                .skip(sampleQuery.offset()).limit(sampleQuery.limit()).toList();
        return new EventSamplesPage(ordered.size(), sampleQuery.limit(), sampleQuery.offset(), page);
    }

    private List<EnrichedEventView> filter(Integer configId, TimeRange timeRange, AttackCategory category, Action action) {
        return events.stream()
                .filter(event -> configId == null || event.configId() == configId)
                .filter(event -> timeRange.includes(event.timestamp()))
                .filter(event -> category == null || event.category() == category)
                .filter(event -> action == null || event.action() == action)
                .toList();
    }
}
