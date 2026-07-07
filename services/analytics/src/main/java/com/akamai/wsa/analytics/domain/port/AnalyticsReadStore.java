package com.akamai.wsa.analytics.domain.port;

import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.SampleQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;

public interface AnalyticsReadStore {
    StatisticsSummary summarize(StatisticsQuery statisticsQuery);

    EventSamplesPage findSamples(SampleQuery sampleQuery);
}
