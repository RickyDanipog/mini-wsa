package com.akamai.wsa.analytics.application;

import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.SampleQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;
import com.akamai.wsa.analytics.domain.query.TimeSeriesQuery;
import com.akamai.wsa.analytics.domain.query.TimeSeriesResult;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsQueryService {

    private final AnalyticsReadStore readStore;

    public AnalyticsQueryService(AnalyticsReadStore readStore) {
        this.readStore = readStore;
    }

    public StatisticsSummary summarize(StatisticsQuery query) {
        return readStore.summarize(query);
    }

    public TimeSeriesResult timeSeries(TimeSeriesQuery query) {
        return readStore.timeSeries(query);
    }

    public EventSamplesPage findSamples(SampleQuery query) {
        return readStore.findSamples(query);
    }
}
