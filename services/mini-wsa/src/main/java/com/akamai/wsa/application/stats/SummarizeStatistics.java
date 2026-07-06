package com.akamai.wsa.application.stats;

/**
 * Inbound use case: aggregate stored events into a {@link StatisticsSummary}
 * for the given configuration and time range.
 */
public interface SummarizeStatistics {

    StatisticsSummary summarize(StatisticsQuery statisticsQuery);
}
