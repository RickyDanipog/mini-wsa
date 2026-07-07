package com.akamai.wsa.analytics.application.stats;

import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;

/** Use case: produce the statistics summary for a query. */
public interface SummarizeStatistics {
    StatisticsSummary summarize(StatisticsQuery statisticsQuery);
}
