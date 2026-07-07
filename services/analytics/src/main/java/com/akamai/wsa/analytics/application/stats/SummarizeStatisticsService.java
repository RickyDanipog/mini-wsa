package com.akamai.wsa.analytics.application.stats;

import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;
import org.springframework.stereotype.Service;

@Service
public class SummarizeStatisticsService implements SummarizeStatistics {

    private final AnalyticsReadStore readStore;

    public SummarizeStatisticsService(AnalyticsReadStore readStore) {
        this.readStore = readStore;
    }

    @Override
    public StatisticsSummary summarize(StatisticsQuery statisticsQuery) {
        return readStore.summarize(statisticsQuery);
    }
}
