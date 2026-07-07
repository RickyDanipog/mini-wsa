package com.akamai.wsa.analytics.application.samples;

import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.SampleQuery;

public interface FetchEventSamples {
    EventSamplesPage fetch(SampleQuery sampleQuery);
}
