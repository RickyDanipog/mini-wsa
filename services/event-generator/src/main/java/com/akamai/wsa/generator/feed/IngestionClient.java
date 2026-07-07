package com.akamai.wsa.generator.feed;

import com.akamai.wsa.generator.model.GeneratedEvent;

import java.util.List;

public interface IngestionClient {

    /** POSTs a batch; returns the accepted count (0 if the batch was rejected). Never throws on a 4xx. */
    int postBatch(List<GeneratedEvent> batch);
}
