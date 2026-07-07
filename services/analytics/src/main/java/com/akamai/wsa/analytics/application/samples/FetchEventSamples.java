package com.akamai.wsa.analytics.application.samples;

import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.SampleQuery;

/** Use case: fetch a page of event samples for a query. */
public interface FetchEventSamples {
    EventSamplesPage fetch(SampleQuery sampleQuery);
}
