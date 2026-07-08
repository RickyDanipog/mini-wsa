package com.akamai.wsa.analytics.application.stats;

import com.akamai.wsa.analytics.domain.query.TimeSeriesQuery;
import com.akamai.wsa.analytics.domain.query.TimeSeriesResult;

public interface BuildTimeSeries {
    TimeSeriesResult build(TimeSeriesQuery timeSeriesQuery);
}
