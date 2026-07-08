package com.akamai.wsa.analytics.domain.port;

import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.SampleQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;
import com.akamai.wsa.analytics.domain.query.TimeSeriesQuery;
import com.akamai.wsa.analytics.domain.query.TimeSeriesResult;

public interface AnalyticsReadStore {
    StatisticsSummary summarize(StatisticsQuery statisticsQuery);

    EventSamplesPage findSamples(SampleQuery sampleQuery);

    TimeSeriesResult timeSeries(TimeSeriesQuery timeSeriesQuery);
}
