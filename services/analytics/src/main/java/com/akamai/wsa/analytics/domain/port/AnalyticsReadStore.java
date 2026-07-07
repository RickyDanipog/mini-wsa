package com.akamai.wsa.analytics.domain.port;

import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.SampleQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;

/** Domain-owned read port: the analytics service reads aggregates and samples through this. */
public interface AnalyticsReadStore {
    StatisticsSummary summarize(StatisticsQuery statisticsQuery);

    EventSamplesPage findSamples(SampleQuery sampleQuery);
}
