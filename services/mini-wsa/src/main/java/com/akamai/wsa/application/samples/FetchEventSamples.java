package com.akamai.wsa.application.samples;

/**
 * Inbound use case: retrieve a filtered, paginated, timestamp-descending page
 * of enriched events.
 */
public interface FetchEventSamples {

    EventSamplesPage fetch(SampleQuery sampleQuery);
}
