package com.akamai.wsa.analytics.application.stats;

import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.domain.query.TimeSeriesQuery;
import com.akamai.wsa.analytics.domain.query.TimeSeriesResult;
import org.springframework.stereotype.Service;

@Service
public class BuildTimeSeriesService implements BuildTimeSeries {

    private final AnalyticsReadStore readStore;

    public BuildTimeSeriesService(AnalyticsReadStore readStore) {
        this.readStore = readStore;
    }

    @Override
    public TimeSeriesResult build(TimeSeriesQuery timeSeriesQuery) {
        return readStore.timeSeries(timeSeriesQuery);
    }
}
