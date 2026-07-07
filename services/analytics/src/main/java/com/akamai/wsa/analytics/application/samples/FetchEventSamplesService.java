package com.akamai.wsa.analytics.application.samples;

import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.SampleQuery;
import org.springframework.stereotype.Service;

@Service
public class FetchEventSamplesService implements FetchEventSamples {

    private final AnalyticsReadStore readStore;

    public FetchEventSamplesService(AnalyticsReadStore readStore) {
        this.readStore = readStore;
    }

    @Override
    public EventSamplesPage fetch(SampleQuery sampleQuery) {
        return readStore.findSamples(sampleQuery);
    }
}
